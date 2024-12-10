package com.example;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A utility class responsible for loading and periodically refreshing database properties from a specified table.
 * It uses the Spring JDBC Template to execute queries against the database and stores the loaded properties in an internal map.
 *
 * @author Anders Swanson
 */
public class DatabasePropertyLoader implements AutoCloseable {
    private static Timer timer;

    private final String table;
    private final JdbcTemplate jdbcTemplate;
    private Map<String, String> properties = new LinkedHashMap<>();

    /**
     * Constructs a new instance of DatabasePropertyLoader that loads and periodically refreshes
     * database properties from the specified table using the provided JdbcTemplate.
     *
     * @param table   the name of the database table containing the properties
     * @param jdbcTemplate the Spring JDBC Template used to execute queries against the database
     * @param refresh the duration between automatic refreshes of the properties, or 0 ms to disable property refresh.
     *                The default refresh rate is 10 minutes if not specified.
     */
    public DatabasePropertyLoader(String table, JdbcTemplate jdbcTemplate, Duration refresh) {
        this.table = table;
        this.jdbcTemplate = jdbcTemplate;
        reload();
        long refreshMillis = Optional.ofNullable(refresh)
                .orElse(Duration.ofMinutes(10))
                .toMillis();
        if (refreshMillis > 0) {
            synchronized (DatabasePropertyLoader.class) {
                if (timer == null) {
                    timer = new Timer(true);
                    timer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            reload();
                        }
                    }, refreshMillis, refreshMillis);
                }
            }
        }
    }

    boolean containsProperty(String key) {
        return properties.containsKey(key);
    }

    Object getProperty(String key) {
        return properties.get(key);
    }

    String[] getPropertyNames() {
        return properties.keySet().toArray(String[]::new);
    }

    /**
     * Reloads the database properties from the specified table into memory.
     * This method executes a SQL query to retrieve all key-value pairs from the table,
     * then updates the internal map of properties with the retrieved values.
     */
    private void reload() {
        String query = "select * from %s".formatted(table);
        List<Property> result = jdbcTemplate.query(query, (rs, rowNum)
                -> new Property(rs.getString("key"), rs.getString("value")));
        properties = new HashMap<>(properties.size());
        for (Property property : result) {
            properties.put(property.key(), property.value());
        }
    }

    @Override
    public void close() throws Exception {
        synchronized (DatabasePropertyLoader.class) {
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
        }
    }
}
