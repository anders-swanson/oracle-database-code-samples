package com.example.security;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

import oracle.jdbc.datasource.impl.OracleDataSource;

public final class DeepDataSecuritySample {
    private static final String SAMPLE_DATABASE_ACCESS_TOKEN = "sample-database-access-token";

    private final DataSource dataSource;

    public DeepDataSecuritySample(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("Expected arguments: <jdbc-url> <jdbc-user> <jdbc-password> [--mode=compat|auto|deepsec]");
        }

        SecurityMode requestedMode = args.length == 4 ? SecurityMode.parse(args[3]) : SecurityMode.COMPAT;
        DeepDataSecuritySample sample = new DeepDataSecuritySample(createDataSource(args[0], args[1], args[2]));
        AccessReport report = sample.run(requestedMode);
        printReport(report);
    }

    AccessReport run(SecurityMode requestedMode) throws Exception {
        SecurityMode effectiveMode = resolveMode(requestedMode);
        if (effectiveMode == SecurityMode.DEEPSEC) {
            throw new IllegalStateException("""
                    Deep Data Security probing succeeded, but this local sample keeps the automated workflow in compatibility mode.
                    Use src/main/resources/sql/deepsec-security.sql and OracleEndUserContextApplier.java as the Deep Data Security handoff points
                    for a 26ai environment with identity tokens and policy administration privileges.
                    """);
        }

        SupportCaseRepository repository = new SupportCaseRepository(
                dataSource,
                new CompatibilityContextApplier(),
                effectiveMode.name()
        );
        boolean applicationContextCreated = repository.resetForCompatibilityMode();
        repository.loadSampleData();

        AccessReport report = runWorkflow(repository, effectiveMode, applicationContextCreated);
        report.validateExpectedResults();
        return report;
    }

    private AccessReport runWorkflow(
            SupportCaseRepository repository,
            SecurityMode effectiveMode,
            boolean applicationContextCreated
    ) throws SQLException {
        var aliceCases = repository.findVisibleCases(SupportActor.ALICE, false);
        var bobCases = repository.findVisibleCases(SupportActor.BOB, false);
        var mariaCases = repository.findVisibleCases(SupportActor.MARIA, false);

        int aliceAssignedUpdateRows = repository.updateStatus(SupportActor.ALICE, false, 1001L, "WAITING_CUSTOMER");
        int aliceUpdateRows = repository.updateStatus(SupportActor.ALICE, false, 1005L, "MITIGATING");
        int bobUpdateRows = repository.updateStatus(SupportActor.BOB, false, 1001L, "PENDING_VENDOR");
        int mariaUpdateRows = repository.updateStatus(SupportActor.MARIA, false, 1005L, "MITIGATING");

        var serviceBeforeElevation = repository.findVisibleCases(SupportActor.ROUTER, false);
        var serviceDuringElevation = repository.findVisibleCases(SupportActor.ROUTER, true);
        var serviceAfterElevation = repository.findVisibleCases(SupportActor.ROUTER, false);
        var auditEvents = repository.listAuditEvents();

        return new AccessReport(
                effectiveMode,
                applicationContextCreated,
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
                auditEvents
        );
    }

    private SecurityMode resolveMode(SecurityMode requestedMode) throws SQLException {
        if (requestedMode == SecurityMode.COMPAT) {
            return SecurityMode.COMPAT;
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean deepSecAvailable = DeepSecDetector.isAvailable(connection);
            if (requestedMode == SecurityMode.DEEPSEC && !deepSecAvailable) {
                throw DeepSecDetector.unavailableException();
            }
            if (requestedMode == SecurityMode.AUTO && !deepSecAvailable) {
                System.out.println("Deep Data Security was not detected in this local Oracle AI Database connection.");
                System.out.println("Falling back to the deterministic compatibility policy path.");
                return SecurityMode.COMPAT;
            }
        }

        return SecurityMode.DEEPSEC;
    }

    static OracleEndUserContextApplier deepSecContextApplier() {
        return new OracleEndUserContextApplier(SAMPLE_DATABASE_ACCESS_TOKEN);
    }

    static OracleDataSource createDataSource(String url, String username, String password) throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private static void printReport(AccessReport report) {
        System.out.printf("Security mode: %s%n", report.effectiveMode());
        System.out.printf("CREATE CONTEXT available: %s%n", report.applicationContextCreated());
        printCases("Alice assigned cases", report.aliceCases());
        printCases("Bob assigned cases", report.bobCases());
        printCases("Maria regional cases", report.mariaCases());
        System.out.printf(Locale.US, "Alice assigned-case update rows: %d%n", report.aliceAssignedUpdateRows());
        System.out.printf(Locale.US, "Alice update rows: %d%n", report.aliceUpdateRows());
        System.out.printf(Locale.US, "Bob update rows: %d%n", report.bobUpdateRows());
        System.out.printf(Locale.US, "Maria update rows: %d%n", report.mariaUpdateRows());
        printCases("Service before elevation", report.serviceBeforeElevation());
        printCases("Service during elevation", report.serviceDuringElevation());
        printCases("Service after elevation", report.serviceAfterElevation());
        System.out.printf(Locale.US, "Audit events captured: %d%n", report.auditEvents().size());
    }

    private static void printCases(String heading, Iterable<SupportCaseView> cases) {
        System.out.println();
        System.out.println(heading + ":");
        boolean any = false;
        for (SupportCaseView supportCase : cases) {
            any = true;
            System.out.printf(
                    Locale.US,
                    "  %d | %s | %s | %s | ssn=%s | %s%n",
                    supportCase.caseId(),
                    supportCase.tenantId(),
                    supportCase.region(),
                    supportCase.status(),
                    supportCase.ssn(),
                    supportCase.policyReason()
            );
        }
        if (!any) {
            System.out.println("  no rows visible");
        }
    }
}
