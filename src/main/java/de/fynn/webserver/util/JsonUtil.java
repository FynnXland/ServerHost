package de.fynn.webserver.util;

/**
 * Shared JSON string escaping to avoid duplicate implementations.
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
