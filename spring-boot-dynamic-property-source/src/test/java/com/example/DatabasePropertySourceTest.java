package com.example;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
public class DatabasePropertySourceTest {
    /**
     * Use a containerized Oracle Database instance to test the Database property source.
     */
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.5-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(2))
            .withUsername("testuser")
            .withPassword(("testpwd"));

    /**
     * Dynamically configure Spring Boot properties to use the Testcontainers database.
     */
    @BeforeAll
    static void setUp() throws SQLException {
        oracleContainer.start();
        System.setProperty("JDBC_URL", oracleContainer.getJdbcUrl());
        System.setProperty("USERNAME", oracleContainer.getUsername());
        System.setProperty("PASSWORD", oracleContainer.getPassword());

        // Configure a datasource for the Oracle Database container.
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
        dataSource.setURL(oracleContainer.getJdbcUrl());
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                create table spring_property (
                key varchar2(255) primary key not null ,
                value varchar2(255) not null
            )""");
            stmt.executeUpdate(" insert into spring_property (key, value) values ('property1', 'initial value')");
        }
    }

    @Autowired
    PropertyService propertyService;

    @Autowired
    DatabaseProperties databaseProperties;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void propertySourceTest() throws InterruptedException {
        System.out.println("Starting Property Source Test");

        String property1 = propertyService.getProperty1();
        assertThat(property1).isNotNull();
        assertThat(property1).isEqualTo("initial value");
        System.out.println("Value of 'property1': " + property1);


        System.out.println("Updating Property 'property1'");
        propertyService.updateProperty(new Property("property1", "updated"));

        // Wait for property to be refreshed
        Thread.sleep(databaseProperties.getPropertyRefreshInterval().plusMillis(200));

        System.out.println("Reloading PropertyService Bean");
        ConfigurableApplicationContext configContext = (ConfigurableApplicationContext) applicationContext;
        DefaultSingletonBeanRegistry registry = (DefaultSingletonBeanRegistry) configContext.getBeanFactory();

        // Destroy the existing bean instance
        registry.destroySingleton("propertyService");

        // Re-create the bean
        propertyService = (PropertyService) applicationContext.getBean("propertyService");

        // Verify bean has reloaded the new property value
        property1 = propertyService.getProperty1();
        assertThat(property1).isNotNull();
        assertThat(property1).isEqualTo("updated");
        System.out.println("New value of 'property1': " + property1);
    }
}
