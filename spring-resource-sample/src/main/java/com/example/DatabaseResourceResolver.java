package com.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseResourceResolver implements ResourceLoaderAware, ProtocolResolver {

    private final JdbcTemplate jdbcTemplate;
    private final String table;
    private final String blobColumn;
    private final String fileNameColumn;

    public DatabaseResourceResolver(JdbcTemplate jdbcTemplate,
                                    @Value("${databaseResource.table:spring_resource}") String table,
                                    @Value("${databaseResource.blobColumn:file_data}") String blobColumn,
                                    @Value("${databaseResource.fileNameColumn:file_name}") String fileNameColumn) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = table;
        this.blobColumn = blobColumn;
        this.fileNameColumn = fileNameColumn;
    }

    @Override
    public Resource resolve(String location, ResourceLoader resourceLoader) {
        if (location.startsWith(DatabaseLocation.PROTOCOL_PREFIX)) {
            String fileName = location.substring(DatabaseLocation.PROTOCOL_PREFIX.length());
            DatabaseLocation databaseLocation = new DatabaseLocation(table, blobColumn, fileNameColumn, fileName);
            return new DatabaseResource(jdbcTemplate, databaseLocation);
        }
        return null;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        if (DefaultResourceLoader.class.isAssignableFrom(resourceLoader.getClass())) {
            ((DefaultResourceLoader) resourceLoader).addProtocolResolver(this);
        }
    }
}
