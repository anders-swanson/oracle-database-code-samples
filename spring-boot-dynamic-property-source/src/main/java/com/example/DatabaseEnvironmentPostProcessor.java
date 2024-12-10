package com.example;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.ClassUtils;

import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

/**
 * A Spring Boot {@link EnvironmentPostProcessor} implementation that loads database properties
 * after the application has been initialized but before it starts up.
 */
public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    /**
     * Post-processes the Spring environment by loading database properties and adding them as property sources.
     * This method is called after the application has been initialized but before it starts up.
     * It creates a JdbcTemplate from the Spring environment, loads the property source properties,
     * and adds the database property sources to the collection of Spring property sources.
     *
     * @param environment the Spring environment to be processed
     * @param application the Spring application instance
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Create a JdbcTemplate from the Spring environment
        Binder binder = Binder.get(environment);
        DataSourceProperties dataSourceProperties = binder.bind("spring.datasource", Bindable.of(DataSourceProperties.class))
                .orElse(new DataSourceProperties());
        DataSource dataSource = dataSourceProperties.initializeDataSourceBuilder()
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // Load the property source properties from the Spring environment
        DatabaseProperties databaseProperties = binder.bind(
                DatabaseProperties.PREFIX,
                Bindable.of(DatabaseProperties.class)
        ).orElse(new DatabaseProperties());
        List<DatabaseProperties.PropertySource> databasePropertySources = databaseProperties.getPropertySources();
        Duration refreshInterval = databaseProperties.getPropertyRefreshInterval();

        MutablePropertySources propertySources = environment.getPropertySources();
        for (DatabaseProperties.PropertySource source : databasePropertySources) {
            DatabasePropertyLoader propertyLoader = new DatabasePropertyLoader(source.getTable(), jdbcTemplate, refreshInterval);
            DatabasePropertySource propertySource = new DatabasePropertySource(source.getTable(), propertyLoader);

            // Add the database property source to the collection of Spring property sources
            if (propertySources.contains(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                propertySources.addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
            } else {
                propertySources.addFirst(propertySource);
            }
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
