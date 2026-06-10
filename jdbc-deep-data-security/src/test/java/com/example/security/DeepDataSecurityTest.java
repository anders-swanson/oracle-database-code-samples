package com.example.security;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DeepDataSecurityTest {
    @Container
    static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd");

    @Test
    void enforcesSupportCaseGuardrailsWithCompatibilityPolicies() throws Exception {
        DeepDataSecuritySample sample = new DeepDataSecuritySample(DeepDataSecuritySample.createDataSource(
                oracleContainer.getJdbcUrl(),
                oracleContainer.getUsername(),
                oracleContainer.getPassword()
        ));

        AccessReport report = sample.run(SecurityMode.COMPAT);

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
}
