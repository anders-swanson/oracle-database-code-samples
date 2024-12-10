package com.example;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = DatabaseProperties.PREFIX)
@Component
public class DatabaseProperties {
    public static final String PREFIX = "database";

    private Duration propertyRefreshInterval;

    private List<PropertySource> propertySources;

    public static class PropertySource {
        private String table;

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }
    }

    public Duration getPropertyRefreshInterval() {
        return propertyRefreshInterval;
    }

    public void setPropertyRefreshInterval(Duration propertyRefreshInterval) {
        this.propertyRefreshInterval = propertyRefreshInterval;
    }

    public List<PropertySource> getPropertySources() {
        return propertySources;
    }

    public void setPropertySources(List<PropertySource> propertySources) {
        this.propertySources = propertySources;
    }
}
