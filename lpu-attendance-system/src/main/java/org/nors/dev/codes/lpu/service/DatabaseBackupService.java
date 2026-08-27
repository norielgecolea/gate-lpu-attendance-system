package org.nors.dev.codes.lpu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.JdbcBackupMeta;
import org.nors.dev.codes.lpu.dto.JdbcBackupSequence;
import org.nors.dev.codes.lpu.dto.JdbcBackupTable;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DatabaseBackupService {

    private static final Logger log = LogManager.getLogger(DatabaseBackupService.class);
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String SCHEMA = "public";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public DatabaseBackupService(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    public void dumpToDirectory(Path destination) {
        try {
            Files.createDirectories(destination);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ");
                }
                List<JdbcBackupTable> tables = new ArrayList<>();
                for (String table : listBaseTables(connection)) {
                    List<String> columns = listColumns(connection, table);
                    String file = table + ".csv";
                    Path csv = destination.resolve(file);
                    copyOut(connection, table, columns, csv);
                    tables.add(new JdbcBackupTable(table, columns, file));
                }
                List<JdbcBackupSequence> sequences = listSequences(connection);
                JdbcBackupMeta meta = new JdbcBackupMeta(
                        JdbcBackupMeta.ENGINE,
                        JdbcBackupMeta.VERSION,
                        tables,
                        sequences
                );
                objectMapper.writeValue(destination.resolve("meta.json").toFile(), meta);
                connection.commit();
            }
        } catch (SQLException | IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Database dump failed: " + ex.getMessage(),
                    ex
            );
        }
    }

    public void restoreFromDirectory(Path databaseDir) {
        Path metaFile = databaseDir.resolve("meta.json");
        if (!Files.isRegularFile(metaFile)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Not a valid LPU attendance backup (missing database/meta.json)"
            );
        }
        JdbcBackupMeta meta;
        try {
            meta = objectMapper.readValue(metaFile.toFile(), JdbcBackupMeta.class);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Backup database/meta.json is not valid", ex);
        }
        if (!JdbcBackupMeta.ENGINE.equals(meta.engine()) || meta.version() != JdbcBackupMeta.VERSION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported database backup format");
        }
        if (meta.tables() == null || meta.tables().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Backup contains no tables");
        }

        terminateOtherBackends();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL session_replication_role = replica");
                String truncateList = meta.tables().stream()
                        .map(table -> qualify(table.name()))
                        .collect(Collectors.joining(", "));
                statement.execute("TRUNCATE TABLE " + truncateList + " RESTART IDENTITY CASCADE");
            }
            for (JdbcBackupTable table : meta.tables()) {
                validateTable(table);
                Path csv = databaseDir.resolve(table.file()).normalize();
                if (!csv.startsWith(databaseDir)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid backup table file: " + table.file());
                }
                if (!Files.isRegularFile(csv)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Backup is missing data for table " + table.name()
                    );
                }
                copyIn(connection, table.name(), table.columns(), csv);
            }
            if (meta.sequences() != null) {
                restoreSequences(connection, meta.sequences());
            }
            connection.commit();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (SQLException | IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Database restore failed: " + ex.getMessage(),
                    ex
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

    private List<String> listBaseTables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = ? AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """
        )) {
            statement.setString(1, SCHEMA);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
        }
        return tables;
    }

    private List<String> listColumns(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """
        )) {
            statement.setString(1, SCHEMA);
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    columns.add(rows.getString(1));
                }
            }
        }
        if (columns.isEmpty()) {
            throw new SQLException("Table has no columns: " + table);
        }
        return columns;
    }

    private List<JdbcBackupSequence> listSequences(Connection connection) throws SQLException {
        List<JdbcBackupSequence> sequences = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT sequencename, COALESCE(last_value, 1), COALESCE(is_called, false)
                FROM pg_sequences
                WHERE schemaname = ?
                ORDER BY sequencename
                """
        )) {
            statement.setString(1, SCHEMA);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    sequences.add(new JdbcBackupSequence(
                            rows.getString(1),
                            rows.getLong(2),
                            rows.getBoolean(3)
                    ));
                }
            }
        }
        return sequences;
    }

    private void restoreSequences(Connection connection, List<JdbcBackupSequence> sequences) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT setval(?::regclass, ?, ?)")) {
            for (JdbcBackupSequence sequence : sequences) {
                if (!SAFE_NAME.matcher(sequence.name()).matches()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sequence name in backup");
                }
                statement.setString(1, SCHEMA + "." + sequence.name());
                statement.setLong(2, sequence.lastValue());
                statement.setBoolean(3, sequence.isCalled());
                statement.execute();
            }
        }
    }

    private void copyOut(Connection connection, String table, List<String> columns, Path csv)
            throws SQLException, IOException {
        String sql = "COPY " + qualify(table) + " (" + columnList(columns) + ") TO STDOUT WITH (FORMAT csv, ENCODING 'UTF8')";
        try (Writer writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            copyManager(connection).copyOut(sql, writer);
        }
    }

    private void copyIn(Connection connection, String table, List<String> columns, Path csv)
            throws SQLException, IOException {
        String sql = "COPY " + qualify(table) + " (" + columnList(columns) + ") FROM STDIN WITH (FORMAT csv, ENCODING 'UTF8')";
        try (Reader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            copyManager(connection).copyIn(sql, reader);
        }
    }

    private static CopyManager copyManager(Connection connection) throws SQLException {
        return connection.unwrap(PGConnection.class).getCopyAPI();
    }

    private static void validateTable(JdbcBackupTable table) {
        if (table == null || !SAFE_NAME.matcher(table.name()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid table name in backup");
        }
        if (table.columns() == null || table.columns().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Backup table is missing columns: " + table.name());
        }
        for (String column : table.columns()) {
            if (!SAFE_NAME.matcher(column).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid column name in backup");
            }
        }
        String file = table.file() == null ? "" : table.file();
        if (!file.equals(table.name() + ".csv") || file.contains("/") || file.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid backup table file: " + file);
        }
    }

    static String quoteIdent(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private static String qualify(String table) {
        if (!SAFE_NAME.matcher(table).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid table name: " + table);
        }
        return quoteIdent(SCHEMA) + "." + quoteIdent(table);
    }

    private static String columnList(List<String> columns) {
        return columns.stream().map(DatabaseBackupService::quoteIdent).collect(Collectors.joining(", "));
    }
}
