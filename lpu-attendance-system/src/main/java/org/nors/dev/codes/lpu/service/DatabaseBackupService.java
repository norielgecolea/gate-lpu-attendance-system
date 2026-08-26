package org.nors.dev.codes.lpu.service;

import com.zaxxer.hikari.HikariDataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DatabaseBackupService {

    private static final Logger log = LogManager.getLogger(DatabaseBackupService.class);
    private static final Pattern JDBC_URL = Pattern.compile(
            "^jdbc:postgresql://([^:/?]+)(?::(\\d+))?/([^?]+)(?:\\?.*)?$"
    );
    private static final Duration DUMP_TIMEOUT = Duration.ofMinutes(60);
    private static final Duration RESTORE_TIMEOUT = Duration.ofMinutes(60);
    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(5);

    private final DataSource dataSource;
    private final PostgresTarget target;

    public DatabaseBackupService(
            DataSource dataSource,
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password
    ) {
        this.dataSource = dataSource;
        this.target = parseJdbcUrl(jdbcUrl, username, password);
    }

    public void ensureToolsAvailable() {
        if (!commandWorks("pg_dump")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "pg_dump is not available on this server. Install postgresql-client to create backups."
            );
        }
        if (!commandWorks("psql")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "psql is not available on this server. Install postgresql-client to restore backups."
            );
        }
    }

    public void dumpToFile(Path destination) {
        ensureToolsAvailable();
        List<String> command = new ArrayList<>();
        command.add("pg_dump");
        command.addAll(connectionArgs());
        command.add("--no-owner");
        command.add("--no-acl");
        command.add("--clean");
        command.add("--if-exists");
        command.add("--format=plain");

        int exit = runToFile(command, destination, DUMP_TIMEOUT, "pg_dump");
        if (exit != 0) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Database dump failed (pg_dump exit " + exit + ")"
            );
        }
        try {
            if (Files.size(destination) == 0) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Database dump was empty");
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read database dump", ex);
        }
    }

    public void restoreFromFile(Path sqlFile) {
        ensureToolsAvailable();
        terminateOtherBackends();
        List<String> command = new ArrayList<>();
        command.add("psql");
        command.addAll(connectionArgs());
        command.add("--single-transaction");
        command.add("-v");
        command.add("ON_ERROR_STOP=1");
        command.add("--file");
        command.add(sqlFile.toAbsolutePath().toString());

        int exit = runToFile(command, null, RESTORE_TIMEOUT, "psql");
        if (exit != 0) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Database restore failed (psql exit " + exit + ")"
            );
        }
        evictPoolConnections();
    }

    void terminateOtherBackends() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    SELECT pg_terminate_backend(pid)
                    FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND pid <> pg_backend_pid()
                      AND backend_type = 'client backend'
                    """
            );
        } catch (SQLException ex) {
            log.warn("Could not terminate other database sessions before restore: {}", ex.toString());
        }
    }

    private void evictPoolConnections() {
        if (dataSource instanceof HikariDataSource hikari) {
            try {
                hikari.getHikariPoolMXBean().softEvictConnections();
            } catch (Exception ex) {
                log.warn("Could not evict pool connections after restore: {}", ex.toString());
            }
        }
    }

    static PostgresTarget parseJdbcUrl(String jdbcUrl, String username, String password) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("Database URL is required");
        }
        Matcher matcher = JDBC_URL.matcher(jdbcUrl.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported database URL: " + jdbcUrl);
        }
        String host = matcher.group(1);
        String port = matcher.group(2) == null ? "5432" : matcher.group(2);
        String database = matcher.group(3);
        return new PostgresTarget(host, port, database, username, password);
    }

    private List<String> connectionArgs() {
        return List.of(
                "--host=" + target.host(),
                "--port=" + target.port(),
                "--username=" + target.username(),
                "--dbname=" + target.database(),
                "--no-password"
        );
    }

    private int runToFile(List<String> command, Path stdoutFile, Duration timeout, String tool) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (target.password() != null) {
            builder.environment().put("PGPASSWORD", target.password());
        }
        builder.redirectErrorStream(false);
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    tool + " could not be started. Install postgresql-client.",
                    ex
            );
        }

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread errorDrain = Thread.ofVirtual().name(tool + "-stderr").start(() -> drain(process.getErrorStream(), stderr));
        try {
            if (stdoutFile != null) {
                try (OutputStream out = Files.newOutputStream(stdoutFile)) {
                    process.getInputStream().transferTo(out);
                }
            } else {
                process.getInputStream().transferTo(OutputStream.nullOutputStream());
            }
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        tool + " timed out after " + timeout.toMinutes() + " minutes"
                );
            }
            errorDrain.join(2_000);
            int exit = process.exitValue();
            if (exit != 0) {
                String details = stderr.toString(StandardCharsets.UTF_8).strip();
                log.error("{} failed with exit {}: {}", tool, exit, details);
            }
            return exit;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, tool + " was interrupted", ex);
        } catch (IOException ex) {
            process.destroyForcibly();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, tool + " I/O failed", ex);
        }
    }

    private static void drain(InputStream stream, ByteArrayOutputStream sink) {
        try {
            stream.transferTo(sink);
        } catch (IOException ignored) {
            // Process ended.
        }
    }

    private static boolean commandWorks(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            process.getErrorStream().transferTo(OutputStream.nullOutputStream());
            boolean finished = process.waitFor(VERSION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    record PostgresTarget(String host, String port, String database, String username, String password) {
    }
}
