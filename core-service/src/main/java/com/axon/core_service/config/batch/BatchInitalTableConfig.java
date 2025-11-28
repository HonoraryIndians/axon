package com.axon.core_service.config.batch;

import javax.sql.DataSource;
import java.util.Optional;

import org.springframework.boot.autoconfigure.batch.BatchDataSourceScriptDatabaseInitializer;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(BatchProperties.class)
public class BatchInitalTableConfig {
    /**
     * Create a BatchDataSourceScriptDatabaseInitializer that initializes Spring Batch metadata only when the batch tables are not present.
     *
     * When batch metadata is detected the initializer is configured with mode = NEVER; when absent it is configured with mode = ALWAYS and continueOnError = false.
     *
     * @param dataSource the DataSource to inspect and to initialize if needed
     * @param properties BatchProperties used to obtain JDBC settings (including table prefix)
     * @return a BatchDataSourceScriptDatabaseInitializer configured to skip initialization if metadata exists or to run schema initialization otherwise
     */

    @Bean
    BatchDataSourceScriptDatabaseInitializer batchInitializer(DataSource dataSource, BatchProperties properties) {
        DatabaseInitializationSettings settings = BatchDataSourceScriptDatabaseInitializer.getSettings(dataSource, properties.getJdbc());
        if (isBatchMetadataPresent(dataSource, properties)) {
            settings.setMode(DatabaseInitializationMode.NEVER);
        } else {
            settings.setMode(DatabaseInitializationMode.ALWAYS);
            settings.setContinueOnError(false);
        }
        return new BatchDataSourceScriptDatabaseInitializer(dataSource, settings);
    }

    /**
     * Checks whether Spring Batch metadata tables already exist by checking INFORMATION_SCHEMA.
     *
     * Uses the JDBC table prefix from {@code properties.getJdbc().getTablePrefix()}, defaulting to {@code "BATCH_"} when not set.
     *
     * @param dataSource the DataSource to run the probe query against
     * @param properties Batch properties that may contain a JDBC table prefix
     * @return {@code true} if the JOB_INSTANCE table exists (metadata present), {@code false} otherwise
     */
    private boolean isBatchMetadataPresent(DataSource dataSource, BatchProperties properties) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String tablePrefix = Optional.ofNullable(properties.getJdbc().getTablePrefix()).orElse("BATCH_");
        String jobInstanceTable = tablePrefix + "JOB_INSTANCE";
        try {
            // Check if table exists in INFORMATION_SCHEMA (works for both empty and populated tables)
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                jobInstanceTable
            );
            return count != null && count > 0;
        } catch (DataAccessException ex) {
            return false;
        }
    }


}