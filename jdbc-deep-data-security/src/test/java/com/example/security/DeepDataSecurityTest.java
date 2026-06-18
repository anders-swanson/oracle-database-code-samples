package com.example.security;

import oracle.jdbc.datasource.impl.OracleDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DeepDataSecurityTest {
    private static final String PASSWORD = "testpwd";

    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword(PASSWORD);

    @BeforeAll
    static void setUpDatabase() throws Exception {
        runScriptAsSys("sql/deep-data-security-demo.sql");
    }

    @Test
    void dataGrantsFilterRowsMaskCellsAndGuardUpdatesForJdbcSessions() throws Exception {
        List<Employee> marvinRows = employeesVisibleTo("manderson");
        List<Employee> emmaRows = employeesVisibleTo("ebaker");

        assertThat(marvinRows).extracting(Employee::employeeId).containsExactly(200, 400, 500);
        assertThat(marvinRows).filteredOn(row -> row.employeeId() == 200)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.ssn()).isEqualTo("457-55-5462");
                    assertThat(row.ssnAuthorized()).isTrue();
                });
        assertThat(marvinRows).filteredOn(row -> row.employeeId() != 200)
                .allSatisfy(row -> {
                    assertThat(row.ssn()).isNull();
                    assertThat(row.ssnAuthorized()).isFalse();
                });

        assertThat(emmaRows).extracting(Employee::employeeId).containsExactly(400);
        assertThat(emmaRows).singleElement().satisfies(row -> {
            assertThat(row.email()).isEqualTo("ebaker");
            assertThat(row.ssn()).isEqualTo("733-02-9821");
        });

        assertThat(marvinRows).filteredOn(row -> row.employeeId() == 200)
                .singleElement()
                .satisfies(row -> assertThat(row.canUpdatePhone()).isTrue());
        assertThat(marvinRows).filteredOn(row -> row.employeeId() != 200)
                .allSatisfy(row -> assertThat(row.canUpdatePhone()).isFalse());
    }

    private static List<Employee> employeesVisibleTo(String endUser) throws SQLException {
        try (Connection connection = dataSource(endUser).getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     select employee_id,
                            first_name,
                            last_name,
                            email,
                            manager,
                            ssn,
                            salary,
                            phone,
                            ORA_IS_COLUMN_AUTHORIZED(ssn) as ssn_authorized,
                            ORA_CHECK_DATA_PRIVILEGE(emp, 'UPDATE', phone) as can_update_phone
                     from hr.employees emp
                     order by employee_id
                     """)) {
            List<Employee> employees = new ArrayList<>();
            while (resultSet.next()) {
                employees.add(new Employee(
                        resultSet.getInt("employee_id"),
                        resultSet.getString("first_name"),
                        resultSet.getString("last_name"),
                        resultSet.getString("email"),
                        resultSet.getString("manager"),
                        resultSet.getString("ssn"),
                        resultSet.getInt("salary"),
                        resultSet.getString("phone"),
                        resultSet.getBoolean("ssn_authorized"),
                        resultSet.getBoolean("can_update_phone")
                ));
            }
            return employees;
        }
    }

    private static OracleDataSource dataSource(String endUser) throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(oracle.getJdbcUrl());
        dataSource.setUser("\"" + endUser + "\"");
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

    private static void runScriptAsSys(String resourcePath) throws Exception {
        String containerPath = "/tmp/" + resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        oracle.copyFileToContainer(MountableFile.forClasspathResource(resourcePath), containerPath);
        ExecResult result = oracle.execInContainer("sqlplus", "-L", "sys / as sysdba", "@" + containerPath);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("""
                    Failed to run %s.
                    stdout:
                    %s
                    stderr:
                    %s
                    """.formatted(resourcePath, result.getStdout(), result.getStderr()));
        }
    }

    private record Employee(
            int employeeId,
            String firstName,
            String lastName,
            String email,
            String manager,
            String ssn,
            int salary,
            String phone,
            boolean ssnAuthorized,
            boolean canUpdatePhone
    ) {
    }
}
