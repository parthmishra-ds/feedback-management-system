package edu.sau.feedback;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the REAL database/schema.sql against in-memory H2 (MySQL mode) so tests exercise the same
 * tables, CHECK constraints, ENUMs, foreign keys, views and 18 categories as production.
 * Only the MySQL-specific CREATE DATABASE / USE statements are stripped.
 */
@TestConfiguration
public class H2SchemaConfig {
    @Bean
    DataSourceInitializer schemaInitializer(DataSource ds) throws Exception {
        String sql = Files.readString(Path.of("../database/schema.sql"), StandardCharsets.UTF_8)
                .replaceAll("(?is)CREATE DATABASE[^;]*;", "")
                .replaceAll("(?i)\\bUSE\\s+\\w+\\s*;", "");
        ResourceDatabasePopulator p = new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
        DataSourceInitializer init = new DataSourceInitializer();
        init.setDataSource(ds);
        init.setDatabasePopulator(p);
        return init;
    }
}
