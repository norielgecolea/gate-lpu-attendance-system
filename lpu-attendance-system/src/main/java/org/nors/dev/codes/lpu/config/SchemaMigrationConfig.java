package org.nors.dev.codes.lpu.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Applies idempotent DDL patches before Hibernate schema validation runs.
 * Docker init scripts handle fresh database volumes; this covers upgrades.
 */
@Configuration
public class SchemaMigrationConfig {

    @Bean
    public SchemaMigrator schemaMigrator(DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/schema-migrations.sql"));
        populator.setContinueOnError(false);
        DatabasePopulatorUtils.execute(populator, dataSource);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
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

        return new SchemaMigrator();
    }

    /** Marker bean so Hibernate can {@code @DependsOn} migration completion. */
    public static final class SchemaMigrator {
    }
}
