package de.fynn.webserver.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.fynn.webserver.WebServerPlugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HttpWebServer {

    private final WebServerPlugin plugin;
    private final int port;
    private final String webRoot;
    private final String indexFile;
    private final boolean accessLog;
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

    public HttpWebServer(WebServerPlugin plugin, int port, String webRoot, String indexFile, boolean accessLog) {
        this.plugin = plugin;
        this.port = port;
        this.webRoot = webRoot;
        this.indexFile = indexFile;
        this.accessLog = accessLog;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new StaticFileHandler());
            executor = Executors.newFixedThreadPool(4);
            server.setExecutor(executor);
            server.start();
            running = true;
            plugin.getLogger().info("Webserver gestartet auf Port " + port);
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

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Sicherheit: Pfad-Traversal verhindern
            if (path.contains("..")) {
                sendError(exchange, 403, "Forbidden");
                return;
            }

            // Wenn Pfad auf / endet, index-Datei anhängen
            if (path.endsWith("/")) {
                path = path + indexFile;
            }

            File file = new File(webRoot, path);

            // Wenn Datei ein Verzeichnis ist, index-Datei suchen
            if (file.isDirectory()) {
                file = new File(file, indexFile);
            }

            if (!file.exists() || !file.isFile()) {
                // SPA-Fallback: Wenn Datei nicht existiert, index.html ausliefern
                File indexFallback = new File(webRoot, indexFile);
                if (indexFallback.exists()) {
                    serveFile(exchange, indexFallback, path);
                } else {
                    sendError(exchange, 404, "Not Found");
                }
                return;
            }

            // Sicherheit: Datei muss innerhalb des webRoot liegen
            if (!file.getCanonicalPath().startsWith(new File(webRoot).getCanonicalPath())) {
                sendError(exchange, 403, "Forbidden");
                return;
            }

            serveFile(exchange, file, path);
        }

        private void serveFile(HttpExchange exchange, File file, String requestPath) throws IOException {
            byte[] data = Files.readAllBytes(file.toPath());
            String mimeType = getMimeType(file.getName());

            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
            exchange.sendResponseHeaders(200, data.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }

            if (accessLog) {
                plugin.getLogger().info(exchange.getRemoteAddress().getAddress().getHostAddress()
                        + " - " + exchange.getRequestMethod() + " " + requestPath
                        + " -> " + file.getName() + " (" + data.length + " bytes)");
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            String html = "<!DOCTYPE html><html><head><title>" + code + " " + message
                    + "</title></head><body><h1>" + code + " " + message + "</h1></body></html>";
            byte[] data = html.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(code, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }

        private String getMimeType(String fileName) {
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex >= 0) {
                String ext = fileName.substring(dotIndex).toLowerCase();
                return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
            }
            return "application/octet-stream";
        }
    }
}
