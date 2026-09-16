package org.nors.dev.codes.lpu.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Temporary debug ingest for host-freeze investigation. Do not use for app logic. */
final class AgentDebugLog {

    private static final Path LOG = Path.of(
            "/Users/norielgecolea/Desktop/Development/development/LPU/lpu-gate-attendance-system/.cursor/debug-b46486.log");

    static void write(String hypothesisId, String location, String message, String dataJson) {
        // #region agent log
        try {
            Files.createDirectories(LOG.getParent());
            Runtime rt = Runtime.getRuntime();
            String payload = "{\"sessionId\":\"b46486\",\"hypothesisId\":\"" + hypothesisId
                    + "\",\"location\":\"" + location + "\",\"message\":\"" + escape(message)
                    + "\",\"data\":" + (dataJson == null || dataJson.isBlank() ? "{}" : dataJson)
                    + ",\"memMb\":{\"free\":" + (rt.freeMemory() / 1048576)
                    + ",\"total\":" + (rt.totalMemory() / 1048576)
                    + ",\"max\":" + (rt.maxMemory() / 1048576)
                    + "},\"timestamp\":" + System.currentTimeMillis() + "}\n";
            Files.writeString(LOG, payload, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Debug ingest must never affect app behavior.
        }
        // #endregion
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
