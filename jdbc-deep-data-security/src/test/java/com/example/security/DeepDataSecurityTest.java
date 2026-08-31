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
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword(PASSWORD);

    @BeforeAll
    static void setUpDatabase() throws Exception {
        System.out.println("\n=== Starting Oracle AI Database Free and loading Deep Data Security setup ===");
        oracle.start();
        oracle.copyFileToContainer(MountableFile.forClasspathResource("init.sql"), "/tmp/init.sql");
        ExecResult result = oracle.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql");
        System.out.println("Loaded init.sql as SYS in freepdb1.");
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("""
                    Failed to load init.sql.
                    stdout:
                    %s
                    stderr:
                    %s
                    """.formatted(result.getStdout(), result.getStderr()));
        }
    }

    @Test
    void verifyDataGrants() throws Exception {
        System.out.println("\n=== Test: data grants filter rows, mask cells, and guard updates ===");
        List<Employee> marvinRows = employeesVisibleTo("manderson");
        List<Employee> emmaRows = employeesVisibleTo("ebaker");

        printRows("Rows visible to \"manderson\" with default hr.hcm_context.org_id = 10", marvinRows);
        printRows("Rows visible to \"ebaker\" with default hr.hcm_context.org_id = 10", emmaRows);

        assertThat(marvinRows).extracting(Employee::employeeId).containsExactly(200, 400, 500);
        assertThat(marvinRows).extracting(Employee::orgId).containsOnly(10);
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
            assertThat(row.orgId()).isEqualTo(10);
            assertThat(row.ssn()).isEqualTo("733-02-9821");
        });

        assertThat(marvinRows).filteredOn(row -> row.employeeId() == 200)
                .singleElement()
                .satisfies(row -> assertThat(row.canUpdatePhone()).isTrue());
        assertThat(marvinRows).filteredOn(row -> row.employeeId() != 200)
                .allSatisfy(row -> assertThat(row.canUpdatePhone()).isFalse());
    }

    @Test
    void verifyContextForDataGrants() throws Exception {
        System.out.println("\n=== Test: context attributes scope data grants for one JDBC session ===");
        HcmContext defaultContext = securityContextVisibleTo("manderson");
        printContext("Default context for \"manderson\"", defaultContext);

        assertThat(defaultContext).isEqualTo(new HcmContext("manderson", 10, "WORKFORCE"));

        List<Employee> orgTwentyRows = employeesVisibleTo("manderson", 20);

        printRows("Rows visible to \"manderson\" after setting hr.hcm_context.org_id = 20", orgTwentyRows);

        assertThat(orgTwentyRows).extracting(Employee::employeeId).containsExactly(600);
        assertThat(orgTwentyRows).singleElement().satisfies(row -> {
            assertThat(row.email()).isEqualTo("npatel");
            assertThat(row.orgId()).isEqualTo(20);
            assertThat(row.ssn()).isNull();
            assertThat(row.ssnAuthorized()).isFalse();
        });
    }

    private static List<Employee> employeesVisibleTo(String endUser) throws SQLException {
        return employeesVisibleTo(endUser, null);
    }

    private static List<Employee> employeesVisibleTo(String endUser, Integer activeOrgId) throws SQLException {
        try (Connection connection = dataSource(endUser).getConnection();
             Statement statement = connection.createStatement()) {
            System.out.printf("Connected as Deep Sec end user \"%s\".%n", endUser);
            if (activeOrgId != null) {
                setActiveOrg(statement, activeOrgId);
                System.out.printf("Updated this session's hr.hcm_context.org_id to %d.%n", activeOrgId);
            }
            try (ResultSet resultSet = statement.executeQuery("""
                     select employee_id,
                            first_name,
                            last_name,
                            email,
                            manager,
                            org_id,
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
                            resultSet.getInt("org_id"),
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
    }

    private static HcmContext securityContextVisibleTo(String endUser) throws SQLException {
        try (Connection connection = dataSource(endUser).getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     select json_value(ORA_END_USER_CONTEXT, '$.USERNAME') as username,
                            json_value(t.context, '$.org_id' returning number) as org_id,
                            json_value(t.context, '$.scope') as scope
                     from sys.end_user_context t
                     where owner = 'HR' and name = 'HCM_CONTEXT'
                     """)) {
            resultSet.next();
            return new HcmContext(
                    resultSet.getString("username"),
                    resultSet.getInt("org_id"),
                    resultSet.getString("scope")
            );
        }
    }

    private static void setActiveOrg(Statement statement, int activeOrgId) throws SQLException {
        statement.executeUpdate("""
                update sys.end_user_context t
                set t.context.org_id = %d
                where owner = 'HR' and name = 'HCM_CONTEXT'
                """.formatted(activeOrgId));
    }

    private static void printContext(String label, HcmContext context) {
        System.out.printf("%s: username=%s, org_id=%d, scope=%s%n",
                label,
                context.username(),
                context.orgId(),
                context.scope());
    }

    private static void printRows(String label, List<Employee> employees) {
        System.out.println(label + ":");
        employees.forEach(employee -> System.out.printf(
                "  employee_id=%d, email=%s, manager=%s, org_id=%d, ssn=%s, ssn_authorized=%s, can_update_phone=%s%n",
                employee.employeeId(),
                employee.email(),
                employee.manager(),
                employee.orgId(),
                employee.ssn(),
                employee.ssnAuthorized(),
                employee.canUpdatePhone()));
    }

    private static OracleDataSource dataSource(String endUser) throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(oracle.getJdbcUrl());
        dataSource.setUser("\"" + endUser + "\"");
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

    private record Employee(
            int employeeId,
            String firstName,
            String lastName,
            String email,
            String manager,
            int orgId,
            String ssn,
            int salary,
            String phone,
            boolean ssnAuthorized,
            boolean canUpdatePhone
    ) {
    }

    private record HcmContext(String username, int orgId, String scope) {
    }
}
