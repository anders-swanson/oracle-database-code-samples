package com.example;

import org.springframework.core.env.EnumerablePropertySource;

/**
 * A custom {@link org.springframework.core.env.PropertySource} implementation that retrieves properties from a database.
 * This class extends {@link EnumerablePropertySource} and delegates property access to an underlying
 * {@link DatabasePropertyLoader}.
 *
 * @see DatabasePropertyLoader
 */
public class DatabasePropertySource extends EnumerablePropertySource<DatabasePropertyLoader> {
    public DatabasePropertySource(String name, DatabasePropertyLoader source) {
        super(name, source);
    }

    @Override
    public String[] getPropertyNames() {
        return source.getPropertyNames();
    }

    @Override
    public Object getProperty(String name) {
        return source.getProperty(name);
    }

    @Override
    public boolean containsProperty(String name) {
        return source.containsProperty(name);
    }
}
