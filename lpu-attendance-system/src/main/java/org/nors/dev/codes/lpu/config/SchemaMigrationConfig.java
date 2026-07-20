package org.nors.dev.codes.lpu.config;

import javax.sql.DataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Applies idempotent DDL patches before Hibernate schema validation runs.
 * Docker init scripts handle fresh database volumes; this covers upgrades.
 */
@Configuration
public class SchemaMigrationConfig {

    private static final Logger log = LogManager.getLogger(SchemaMigrationConfig.class);

    @Bean
    public SchemaMigrator schemaMigrator(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS app_settings (
                    setting_key   VARCHAR(100) PRIMARY KEY,
                    setting_value VARCHAR(500) NOT NULL,
                    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                )
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS guard_videos (
                    id            BIGSERIAL PRIMARY KEY,
                    file_path     VARCHAR(300) NOT NULL,
                    original_name VARCHAR(300) NOT NULL,
                    content_type  VARCHAR(100) NOT NULL,
                    size_bytes    BIGINT       NOT NULL,
                    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                )
                """);

        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_guard_videos_uploaded
                    ON guard_videos (uploaded_at ASC, id ASC)
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS tap_error_logs (
                    id          BIGSERIAL PRIMARY KEY,
                    identifier  VARCHAR(100) NOT NULL,
                    location    VARCHAR(100),
                    tapped_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                )
                """);

        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_tap_error_logs_tapped
                    ON tap_error_logs (tapped_at DESC, id DESC)
                """);

        Boolean employeesExists = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name = 'employees'
                )
                """,
                Boolean.class
        );
        if (Boolean.TRUE.equals(employeesExists)) {
            jdbc.execute("ALTER TABLE employees ALTER COLUMN department DROP NOT NULL");
            jdbc.execute("ALTER TABLE employees ALTER COLUMN position DROP NOT NULL");
        }

        log.info("Schema migration applied (app_settings, guard_videos, tap_error_logs)");
        return new SchemaMigrator();
    }

    /** Marker bean so Hibernate can {@code @DependsOn} migration completion. */
    public static final class SchemaMigrator {
    }
}
