package de.fynn.webserver.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import de.fynn.webserver.WebServerPlugin;
import de.fynn.webserver.analytics.AnalyticsService;
import de.fynn.webserver.debug.DebugService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;

public class HttpWebServer {

    private final WebServerPlugin plugin;
    private final int port;
    private final String webRoot;
    private final String indexFile;
    private final boolean accessLog;
    private final AnalyticsService analyticsService;
    private final DebugService debugService;
    private final int rateLimitPerSecond;
    private final int rateLimitBurst;
    private final int fileCacheMaxEntries;
    private final String apiKey;
    private final boolean protectStatsWithApiKey;
    private final boolean protectAnalyticsWithApiKey;
    private final boolean deployWebhookEnabled;
    private final boolean sslEnabled;
    private final String sslKeystorePath;
    private final String sslKeystorePassword;
    private final String sslKeyPassword;
    private final String sslKeystoreType;
    private final int cacheMaxAgeSeconds;
    private final boolean gzipEnabled;
    private final String corsAllowedOrigins;
    private final int accessLogMaxEntries;
    private final Path normalizedWebRoot;
    private final ConcurrentHashMap<String, CachedFile> fileCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();
    private final List<String> recentAccessLog = new ArrayList<>();
    private HttpServer server;
    private ExecutorService executor;
    private boolean running = false;

    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry(".html", "text/html; charset=utf-8"),
            Map.entry(".htm", "text/html; charset=utf-8"),
            Map.entry(".css", "text/css; charset=utf-8"),
            Map.entry(".js", "application/javascript; charset=utf-8"),
            Map.entry(".mjs", "application/javascript; charset=utf-8"),
            Map.entry(".jsx", "application/javascript; charset=utf-8"),
            Map.entry(".ts", "application/javascript; charset=utf-8"),
            Map.entry(".tsx", "application/javascript; charset=utf-8"),
            Map.entry(".json", "application/json; charset=utf-8"),
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".ico", "image/x-icon"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".woff", "font/woff"),
            Map.entry(".woff2", "font/woff2"),
            Map.entry(".ttf", "font/ttf"),
            Map.entry(".eot", "application/vnd.ms-fontobject"),
            Map.entry(".xml", "application/xml"),
            Map.entry(".txt", "text/plain; charset=utf-8"),
            Map.entry(".map", "application/json"),
            Map.entry(".webmanifest", "application/manifest+json")
    );

    private static final Set<String> COMPRESSIBLE_EXTENSIONS = Set.of(
            ".html", ".htm", ".css", ".js", ".mjs", ".jsx", ".ts", ".tsx",
            ".json", ".svg", ".xml", ".txt", ".map", ".webmanifest"
    );

    public HttpWebServer(
            WebServerPlugin plugin,
            int port,
            String webRoot,
            String indexFile,
            boolean accessLog,
            AnalyticsService analyticsService,
            DebugService debugService,
            int rateLimitPerSecond,
            int rateLimitBurst,
            int fileCacheMaxEntries,
            String apiKey,
            boolean protectStatsWithApiKey,
            boolean protectAnalyticsWithApiKey,
            boolean deployWebhookEnabled,
            boolean sslEnabled,
            String sslKeystorePath,
            String sslKeystorePassword,
            String sslKeyPassword,
            String sslKeystoreType,
            int cacheMaxAgeSeconds,
            boolean gzipEnabled,
            String corsAllowedOrigins,
            int accessLogMaxEntries
    ) {
        this.plugin = plugin;
        this.port = port;
        this.webRoot = webRoot;
        this.indexFile = indexFile;
        this.accessLog = accessLog;
        this.analyticsService = analyticsService;
        this.debugService = debugService;
        this.rateLimitPerSecond = Math.max(1, rateLimitPerSecond);
        this.rateLimitBurst = Math.max(this.rateLimitPerSecond, rateLimitBurst);
        this.fileCacheMaxEntries = Math.max(10, fileCacheMaxEntries);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.protectStatsWithApiKey = protectStatsWithApiKey;
        this.protectAnalyticsWithApiKey = protectAnalyticsWithApiKey;
        this.deployWebhookEnabled = deployWebhookEnabled;
        this.sslEnabled = sslEnabled;
        this.sslKeystorePath = sslKeystorePath == null ? "" : sslKeystorePath;
        this.sslKeystorePassword = sslKeystorePassword == null ? "" : sslKeystorePassword;
        this.sslKeyPassword = sslKeyPassword == null ? "" : sslKeyPassword;
        this.sslKeystoreType = sslKeystoreType == null || sslKeystoreType.isBlank() ? "PKCS12" : sslKeystoreType;
        this.cacheMaxAgeSeconds = Math.max(0, cacheMaxAgeSeconds);
        this.gzipEnabled = gzipEnabled;
        this.corsAllowedOrigins = corsAllowedOrigins == null || corsAllowedOrigins.isBlank() ? "*" : corsAllowedOrigins.trim();
        this.accessLogMaxEntries = Math.max(10, accessLogMaxEntries);
        this.normalizedWebRoot = Path.of(webRoot).toAbsolutePath().normalize();
    }

    public void start() {
        try {
            server = createServer();
            server.createContext("/", new StaticFileHandler());
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.start();
            running = true;
            plugin.getLogger().info((sslEnabled ? "Webserver (HTTPS bevorzugt)" : "Webserver") + " gestartet auf Port " + port);
            plugin.getLogger().info("Web-Root: " + webRoot);
        } catch (IOException e) {
            plugin.getLogger().severe("Webserver konnte nicht gestartet werden: " + e.getMessage());
            running = false;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(2);
            running = false;
            plugin.getLogger().info("Webserver gestoppt.");
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            executor = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public String buildStatsJson() {
        return plugin.getRuntimeStatsService().snapshot(analyticsService.snapshot()).toJson();
    }

    private HttpServer createServer() throws IOException {
        if (!sslEnabled) {
            return HttpServer.create(new InetSocketAddress(port), 0);
        }
        try {
            SSLContext sslContext = buildSslContext();
            HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(port), 0);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            plugin.getLogger().info("HTTPS aktiviert (Keystore: " + sslKeystorePath + ")");
            return httpsServer;
        } catch (Exception exception) {
            debugService.recordError(plugin, "HTTPS Setup fehlgeschlagen, falle auf HTTP zurück", new RuntimeException(exception));
            return HttpServer.create(new InetSocketAddress(port), 0);
        }
    }

    private SSLContext buildSslContext() throws Exception {
        if (sslKeystorePath.isBlank()) {
            throw new IllegalStateException("ssl.keystore-path ist leer");
        }
        File keystoreFile = new File(sslKeystorePath);
        if (!keystoreFile.exists()) {
            throw new IllegalStateException("Keystore nicht gefunden: " + sslKeystorePath);
        }
        if (sslKeystorePassword.isBlank()) {
            throw new IllegalStateException("ssl.keystore-password fehlt");
        }

        KeyStore keyStore = KeyStore.getInstance(sslKeystoreType);
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            keyStore.load(fis, sslKeystorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        char[] keyPass = sslKeyPassword.isBlank() ? sslKeystorePassword.toCharArray() : sslKeyPassword.toCharArray();
        kmf.init(keyStore, keyPass);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }

    public synchronized List<String> getRecentAccessLog(int limit) {
        int safeLimit = Math.max(1, limit);
        int from = Math.max(0, recentAccessLog.size() - safeLimit);
        return new ArrayList<>(recentAccessLog.subList(from, recentAccessLog.size()));
    }

    private synchronized void appendAccessLog(String line) {
        recentAccessLog.add(line);
        if (recentAccessLog.size() > accessLogMaxEntries) {
            recentAccessLog.remove(0);
        }
    }

    public ApiResult handleStatsApi(HttpExchange exchange, String path, String method) {
        if (!"/api/stats".equals(path)
                && !"/api/stats/analytics".equals(path)) {
            return ApiResult.notHandled();
        }
        try {
            if ("OPTIONS".equalsIgnoreCase(method)) {
                applyCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return new ApiResult(true, 204, 0);
            }
            if ("/api/stats".equals(path) && protectStatsWithApiKey && !isAuthorized(exchange)) {
                applyCorsHeaders(exchange);
                long bytes = sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return new ApiResult(true, 401, bytes);
            }
            if ("/api/stats/analytics".equals(path) && protectAnalyticsWithApiKey && !isAuthorized(exchange)) {
                applyCorsHeaders(exchange);
                long bytes = sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return new ApiResult(true, 401, bytes);
            }
            if (!"GET".equalsIgnoreCase(method)) {
                applyCorsHeaders(exchange);
                long bytes = sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return new ApiResult(true, 405, bytes);
            }

            applyCorsHeaders(exchange);
            String responseBody = "/api/stats/analytics".equals(path)
                    ? analyticsService.snapshot().toJson()
                    : buildStatsJson();
            long bytes = sendJson(exchange, 200, responseBody);
            return new ApiResult(true, 200, bytes);
        } catch (Exception exception) {
            debugService.recordError(plugin, "Stats-API Fehler", exception);
            return ApiResult.notHandled();
        }
    }

    public ApiResult handleLeaderboardApi(HttpExchange exchange, String path, String method) {
        if (!"/api/leaderboard".equals(path)) {
            return ApiResult.notHandled();
        }
        try {
            if ("OPTIONS".equalsIgnoreCase(method)) {
                applyCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return new ApiResult(true, 204, 0);
            }
            if (!"GET".equalsIgnoreCase(method)) {
                applyCorsHeaders(exchange);
                long bytes = sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return new ApiResult(true, 405, bytes);
            }

            applyCorsHeaders(exchange);
            String responseBody = plugin.getKillStatsService().buildLeaderboardJson(3);
            long bytes = sendJson(exchange, 200, responseBody);
            return new ApiResult(true, 200, bytes);
        } catch (Exception exception) {
            debugService.recordError(plugin, "Leaderboard-API Fehler", exception);
            return ApiResult.notHandled();
        }
    }

    public ApiResult handleDeployWebhook(HttpExchange exchange, String path, String method) {
        if (!"/api/deploy".equals(path)) {
            return ApiResult.notHandled();
        }
        try {
            applyCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return new ApiResult(true, 204, 0);
            }
            if (!deployWebhookEnabled) {
                long bytes = sendJson(exchange, 404, "{\"error\":\"Deploy webhook disabled\"}");
                return new ApiResult(true, 404, bytes);
            }
            if (!"POST".equalsIgnoreCase(method)) {
                long bytes = sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return new ApiResult(true, 405, bytes);
            }
            if (!isAuthorized(exchange)) {
                long bytes = sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return new ApiResult(true, 401, bytes);
            }

            String deployIp = "unknown";
            try {
                if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
                    deployIp = exchange.getRemoteAddress().getAddress().getHostAddress();
                }
            } catch (Exception ignored) { }
            plugin.getLogger().info("[Deploy] Webhook ausgelöst von " + deployIp);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.restartWebServer();
                // Clear file cache after deploy so updated files are served immediately
                fileCache.clear();
            });
            long bytes = sendJson(exchange, 202, "{\"status\":\"deploy-triggered\"}");
            return new ApiResult(true, 202, bytes);
        } catch (Exception exception) {
            debugService.recordError(plugin, "Deploy-Webhook Fehler", exception);
            return ApiResult.notHandled();
        }
    }

    private void applyCorsHeaders(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if ("*".equals(corsAllowedOrigins)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        } else if (origin != null) {
            for (String allowed : corsAllowedOrigins.split(",")) {
                if (allowed.trim().equalsIgnoreCase(origin.trim())) {
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
                    exchange.getResponseHeaders().set("Vary", "Origin");
                    break;
                }
            }
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Accept, X-API-Key");
    }

    private long sendJson(HttpExchange exchange, int code, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
        return data.length;
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long startNanos = System.nanoTime();
            String method = safeText(exchange.getRequestMethod(), "UNKNOWN");
            String path = "/";
            int statusCode = 500;
            long bytesWritten = 0;

            try {
                path = safeText(exchange.getRequestURI().getPath(), "/");
                String remoteIp = safeRemoteAddress(exchange);

                if (!allowRequest(remoteIp)) {
                    bytesWritten = sendError(exchange, 429, "Too Many Requests");
                    statusCode = 429;
                    return;
                }

                ApiResult deployResult = handleDeployWebhook(exchange, path, method);
                if (deployResult.handled()) {
                    statusCode = deployResult.statusCode();
                    bytesWritten = deployResult.bytesWritten();
                    return;
                }

                ApiResult apiResult = handleStatsApi(exchange, path, method);
                if (apiResult.handled()) {
                    statusCode = apiResult.statusCode();
                    bytesWritten = apiResult.bytesWritten();
                    return;
                }

                ApiResult leaderboardResult = handleLeaderboardApi(exchange, path, method);
                if (leaderboardResult.handled()) {
                    statusCode = leaderboardResult.statusCode();
                    bytesWritten = leaderboardResult.bytesWritten();
                    return;
                }

                Path resolvedPath= resolvePath(path);
                if (resolvedPath == null) {
                    bytesWritten = sendError(exchange, 403, "Forbidden");
                    statusCode = 403;
                    return;
                }
                File file = resolvedPath.toFile();

                if (!file.exists() || !file.isFile()) {
                    // Only use SPA fallback for non-API paths
                    if (!path.startsWith("/api/")) {
                        File indexFallback = new File(webRoot, indexFile);
                        if (indexFallback.exists()) {
                            bytesWritten = serveFile(exchange, indexFallback, path);
                            statusCode = 200;
                            return;
                        }
                    }
                    bytesWritten = sendError(exchange, 404, "Not Found");
                    statusCode = 404;
                    return;
                }

                bytesWritten = serveFile(exchange, file, path);
                statusCode = 200;
            } catch (Exception exception) {
                debugService.recordError(plugin, "Request-Handling (" + path + ")", exception);
                try {
                    bytesWritten = sendError(exchange, 500, "Internal Server Error");
                    statusCode = 500;
                } catch (Exception responseException) {
                    debugService.recordError(plugin, "500-Response konnte nicht gesendet werden", responseException);
                }
            } finally {
                analyticsService.recordRequest(
                        method,
                        path,
                        statusCode,
                        System.nanoTime() - startNanos,
                        bytesWritten,
                        safeRemoteAddress(exchange)
                );
                appendAccessLog(safeRemoteAddress(exchange) + " " + method + " " + path + " -> " + statusCode + " (" + bytesWritten + "b)");
                try {
                    exchange.close();
                } catch (Exception ignored) {
                    // no-op
                }
            }
        }

        private long serveFile(HttpExchange exchange, File file, String requestPath) throws IOException {
            CachedFile cached = getCachedFile(file);
            byte[] data = cached.data;
            String mimeType = getMimeType(file.getName());

            // ETag support (weak ETag based on content hash)
            String etag = "\"" + computeETag(data) + "\"";
            exchange.getResponseHeaders().set("ETag", etag);
            String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
            if (etag.equals(ifNoneMatch)) {
                exchange.sendResponseHeaders(304, -1);
                return 0;
            }

            exchange.getResponseHeaders().set("Content-Type", mimeType);
            if (cacheMaxAgeSeconds > 0) {
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=" + cacheMaxAgeSeconds);
            } else {
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            }

            // GZip compression for text-based content
            byte[] responseData = data;
            boolean useGzip = false;
            if (gzipEnabled && isCompressible(file.getName()) && data.length > 256) {
                String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
                if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
                    responseData = gzipCompress(data);
                    useGzip = true;
                    exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                }
            }

            exchange.sendResponseHeaders(200, responseData.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseData);
            }

            if (accessLog) {
                String remoteAddr = safeRemoteAddress(exchange);
                plugin.getLogger().info(remoteAddr
                        + " - " + exchange.getRequestMethod() + " " + requestPath
                        + " -> " + file.getName() + " (" + responseData.length + " bytes"
                        + (useGzip ? ", gzip" : "") + ")");
            }
            return responseData.length;
        }

        private long sendError(HttpExchange exchange, int code, String message) throws IOException {
            String html = "<!DOCTYPE html><html><head><title>" + code + " " + message
                    + "</title></head><body><h1>" + code + " " + message + "</h1></body></html>";
            byte[] data = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(code, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
            return data.length;
        }

        private String getMimeType(String fileName) {
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex >= 0) {
                String ext = fileName.substring(dotIndex).toLowerCase();
                return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
            }
            return "application/octet-stream";
        }

        private String safeRemoteAddress(HttpExchange exchange) {
            try {
                if (exchange.getRemoteAddress() == null || exchange.getRemoteAddress().getAddress() == null) {
                    return "unknown";
                }
                return exchange.getRemoteAddress().getAddress().getHostAddress();
            } catch (Exception exception) {
                return "unknown";
            }
        }

        private String safeText(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value;
        }
    }

    private static String computeETag(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Long.toHexString(data.length);
        }
    }

    private static boolean isCompressible(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            return COMPRESSIBLE_EXTENSIONS.contains(fileName.substring(dotIndex).toLowerCase());
        }
        return false;
    }

    private static byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length / 2);
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    public void clearFileCache() {
        fileCache.clear();
    }

    private CachedFile getCachedFile(File file) throws IOException {
        String key = file.getAbsolutePath();
        long lastModified = file.lastModified();
        CachedFile existing = fileCache.get(key);
        if (existing != null && existing.lastModified == lastModified) {
            existing.touch();
            return existing;
        }
        byte[] data = Files.readAllBytes(file.toPath());
        if (fileCache.size() >= fileCacheMaxEntries) {
            // LRU eviction: remove oldest quarter of entries based on last access
            var entries = new java.util.ArrayList<>(fileCache.entrySet());
            entries.sort(java.util.Comparator.comparingLong(e -> e.getValue().lastAccess));
            int toRemove = Math.max(1, entries.size() / 4);
            for (int i = 0; i < toRemove && i < entries.size(); i++) {
                fileCache.remove(entries.get(i).getKey());
            }
        }
        CachedFile updated = new CachedFile(data, lastModified);
        fileCache.put(key, updated);
        return updated;
    }

    private Path resolvePath(String requestPath) {
        String safeRequest = requestPath == null || requestPath.isBlank() ? "/" : requestPath;
        if (safeRequest.endsWith("/")) {
            safeRequest = safeRequest + indexFile;
        }
        String relative = safeRequest.startsWith("/") ? safeRequest.substring(1) : safeRequest;
        Path candidate = normalizedWebRoot.resolve(relative).normalize();
        if (!candidate.startsWith(normalizedWebRoot)) {
            return null;
        }
        // Prevent symlink-based escapes from web root
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(normalizedWebRoot.toRealPath())) {
                return null;
            }
        } catch (IOException e) {
            // File may not exist yet — that's fine, caller handles missing files
        }
        return candidate;
    }

    private boolean allowRequest(String remoteIp) {
        TokenBucket bucket = ipBuckets.computeIfAbsent(remoteIp, ip -> new TokenBucket(rateLimitPerSecond, rateLimitBurst));
        return bucket.tryConsume();
    }

    private boolean isAuthorized(HttpExchange exchange) {
        if (apiKey.isBlank()) {
            return false;
        }
        String header = exchange.getRequestHeaders().getFirst("X-API-Key");
        return apiKey.equals(header);
    }

    private static class CachedFile {
        private final byte[] data;
        private final long lastModified;
        private volatile long lastAccess;

        private CachedFile(byte[] data, long lastModified) {
            this.data = data;
            this.lastModified = lastModified;
            this.lastAccess = System.nanoTime();
        }

        private void touch() {
            this.lastAccess = System.nanoTime();
        }
    }

    private static class TokenBucket {
        private final int refillPerSecond;
        private final int capacity;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(int refillPerSecond, int capacity) {
            this.refillPerSecond = refillPerSecond;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        private synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds <= 0) {
                return;
            }
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefillNanos = now;
        }
    }

    public record ApiResult(boolean handled, int statusCode, long bytesWritten) {
        public static ApiResult notHandled() {
            return new ApiResult(false, 0, 0);
        }
    }
}
