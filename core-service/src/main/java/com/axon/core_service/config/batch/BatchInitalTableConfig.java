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

    private boolean isBatchMetadataPresent(DataSource dataSource, BatchProperties properties) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String tablePrefix = Optional.ofNullable(properties.getJdbc().getTablePrefix()).orElse("BATCH_");
        String jobInstanceTable = tablePrefix + "JOB_INSTANCE";
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM " + jobInstanceTable + " LIMIT 1", Integer.class);
            return true;
        } catch (DataAccessException ex) {
            return false;
        }
    }
}
