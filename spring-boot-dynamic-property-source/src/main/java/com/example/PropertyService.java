package com.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PropertyService {
    private static final String PROPERTY_TABLE = "spring_property";
    private static final String UPDATE_PROPERTY = """
            update %s set value = ? where key = ?
            """.formatted(PROPERTY_TABLE);
    private final JdbcTemplate jdbcTemplate;

    /**
     * The value of 'property1' is dynamically loaded from the database during
     * startup, using Spring's property loader mechanism.
     */
    @Value("${property1}")
    private String property1;

    public PropertyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Used to demonstrate dynamic property loading, using the value of
     * 'property1' loaded from the database property source.
     * @return the current value of property1.
     */
    public String getProperty1() {
        return property1;
    }

    /**
     * Updates an existing property in the database.
     *
     * @param property the updated property object containing the new value and key
     */
    public void updateProperty(Property property) {
        jdbcTemplate.update(UPDATE_PROPERTY, property.value(), property.key());
    }
}
