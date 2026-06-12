package com.example.security;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import oracle.jdbc.datasource.impl.OracleDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DeepDataSecurityTest {
    @Container
    static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd");

    static OracleDataSource dataSource;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        runScriptAsSys("sql/00-testuser-grants.sql");
        runScriptAsTestUser("sql/01-base-schema.sql");
        runScriptAsTestUser("sql/02-compat-security.sql");
        runScriptAsTestUser("sql/03-support-cases.sql");

        dataSource = new OracleDataSource();
        dataSource.setURL(oracleContainer.getJdbcUrl());
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
    }

    @Test
    void enforcesSupportCaseGuardrailsWithCompatibilityPolicies() throws Exception {
        SupportCaseRepository repository = new SupportCaseRepository(
                dataSource,
                new CompatibilityContextApplier(),
                SecurityMode.COMPAT.name()
        );

        AccessReport report = runWorkflow(repository);

        assertThat(report.effectiveMode()).isEqualTo(SecurityMode.COMPAT);
        assertThat(report.aliceCases()).extracting(SupportCaseView::caseId).containsExactly(1001L, 1002L);
        assertThat(report.aliceCases()).allSatisfy(supportCase -> {
            assertThat(supportCase.customerEmail()).startsWith("masked-");
            assertThat(supportCase.ssn()).startsWith("***-**-");
            assertThat(supportCase.internalNotes()).isEqualTo("[redacted by policy]");
        });

        assertThat(report.bobCases()).extracting(SupportCaseView::caseId).containsExactly(1003L);
        assertThat(report.mariaCases()).extracting(SupportCaseView::caseId).containsExactly(1001L, 1002L, 1005L);
        assertThat(report.mariaCases()).allSatisfy(supportCase ->
                assertThat(supportCase.internalNotes()).doesNotStartWith("[redacted")
        );

        assertThat(report.aliceAssignedUpdateRows()).isEqualTo(1);
        assertThat(report.aliceUpdateRows()).isZero();
        assertThat(report.bobUpdateRows()).isZero();
        assertThat(report.mariaUpdateRows()).isEqualTo(1);

        assertThat(report.serviceBeforeElevation()).isEmpty();
        assertThat(report.serviceDuringElevation()).extracting(SupportCaseView::caseId).containsExactly(1004L);
        assertThat(report.serviceAfterElevation()).isEmpty();

        assertThat(report.auditEvents()).anySatisfy(event -> {
            assertThat(event.actorName()).isEqualTo("alice@example.com");
            assertThat(event.operation()).isEqualTo("UPDATE_STATUS");
            assertThat(event.caseId()).isEqualTo(1001L);
            assertThat(event.rowsAffected()).isEqualTo(1);
        });
        assertThat(report.auditEvents()).anySatisfy(event -> {
            assertThat(event.actorName()).isEqualTo("alice@example.com");
            assertThat(event.operation()).isEqualTo("UPDATE_STATUS");
            assertThat(event.caseId()).isEqualTo(1005L);
            assertThat(event.rowsAffected()).isZero();
        });
        assertThat(report.auditEvents()).anySatisfy(event -> {
            assertThat(event.actorName()).isEqualTo("router-service");
            assertThat(event.operation()).isEqualTo("SELECT_CASES");
            assertThat(event.rowsAffected()).isEqualTo(1);
            assertThat(event.elevated()).isTrue();
        });
    }

    private static AccessReport runWorkflow(SupportCaseRepository repository) throws SQLException {
        List<SupportCaseView> aliceCases = repository.findVisibleCases(SupportActor.ALICE, false);
        List<SupportCaseView> bobCases = repository.findVisibleCases(SupportActor.BOB, false);
        List<SupportCaseView> mariaCases = repository.findVisibleCases(SupportActor.MARIA, false);

        int aliceAssignedUpdateRows = repository.updateStatus(SupportActor.ALICE, false, 1001L, "WAITING_CUSTOMER");
        int aliceUpdateRows = repository.updateStatus(SupportActor.ALICE, false, 1005L, "MITIGATING");
        int bobUpdateRows = repository.updateStatus(SupportActor.BOB, false, 1001L, "PENDING_VENDOR");
        int mariaUpdateRows = repository.updateStatus(SupportActor.MARIA, false, 1005L, "MITIGATING");

        List<SupportCaseView> serviceBeforeElevation = repository.findVisibleCases(SupportActor.ROUTER, false);
        List<SupportCaseView> serviceDuringElevation = repository.findVisibleCases(SupportActor.ROUTER, true);
        List<SupportCaseView> serviceAfterElevation = repository.findVisibleCases(SupportActor.ROUTER, false);

        return new AccessReport(
                SecurityMode.COMPAT,
                aliceCases,
                bobCases,
                mariaCases,
                aliceAssignedUpdateRows,
                aliceUpdateRows,
                bobUpdateRows,
                mariaUpdateRows,
                serviceBeforeElevation,
                serviceDuringElevation,
                serviceAfterElevation,
                repository.listAuditEvents()
        );
    }

    private static void runScriptAsSys(String resourcePath) throws Exception {
        runSqlPlusScript("sys / as sysdba", resourcePath);
    }

    private static void runScriptAsTestUser(String resourcePath) throws Exception {
        runSqlPlusScript(oracleContainer.getUsername() + "/" + oracleContainer.getPassword() + "@//localhost:1521/FREEPDB1", resourcePath);
    }

    private static void runSqlPlusScript(String connection, String resourcePath) throws Exception {
        String containerPath = "/tmp/" + resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource(resourcePath), containerPath);
        ExecResult result = oracleContainer.execInContainer("sqlplus", "-L", connection, "@" + containerPath);
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

    private record AccessReport(
            SecurityMode effectiveMode,
            List<SupportCaseView> aliceCases,
            List<SupportCaseView> bobCases,
            List<SupportCaseView> mariaCases,
            int aliceAssignedUpdateRows,
            int aliceUpdateRows,
            int bobUpdateRows,
            int mariaUpdateRows,
            List<SupportCaseView> serviceBeforeElevation,
            List<SupportCaseView> serviceDuringElevation,
            List<SupportCaseView> serviceAfterElevation,
            List<AuditEvent> auditEvents
    ) {
    }
}
