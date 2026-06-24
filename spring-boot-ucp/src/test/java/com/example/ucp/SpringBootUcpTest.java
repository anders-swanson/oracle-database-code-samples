package com.example.ucp;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import javax.sql.DataSource;

import oracle.jdbc.pool.OracleDataSource;
import oracle.ucp.jdbc.PoolDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.datasource.oracleucp.connection-pool-name=spring-boot-ucp")
class SpringBootUcpTest {
    private static final String APP_USERNAME = "testuser";
    private static final String APP_PASSWORD = "testpwd";
    private static final String SYS_PASSWORD = APP_PASSWORD;

    @Container
    @ServiceConnection
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.2-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername(APP_USERNAME)
            .withPassword(APP_PASSWORD)
            .withEnv(Map.of(
                    "ORACLE_PASSWORD", SYS_PASSWORD
            ));

    @Autowired
    private DataSource dataSource;

    @Autowired
    private HarvestingService harvestingService;

    @Autowired
    private ConnectionLabelingService connectionLabelingService;

    @Test
    void usesUcpDataSourceAndRunsSmokeQuery() throws SQLException {
        PoolDataSource poolDataSource = dataSource.unwrap(PoolDataSource.class);

        assertThat(poolDataSource).isNotNull();
        assertThat(queryOne(dataSource)).isEqualTo(1);
    }

    @Test
    void reportsPoolMetricsAfterBorrowAndReturn() throws SQLException {
        PoolDataSource poolDataSource = dataSource.unwrap(PoolDataSource.class);

        try (Connection ignored = poolDataSource.getConnection()) {
            PoolMetrics borrowedMetrics = PoolMetrics.from(poolDataSource);

            assertThat(borrowedMetrics.statisticsAvailable()).isTrue();
            assertThat(borrowedMetrics.totalConnections()).isGreaterThanOrEqualTo(1);
            assertThat(borrowedMetrics.borrowedConnections()).isEqualTo(1);
            assertThat(borrowedMetrics.cumulativeConnectionBorrowedCount()).isGreaterThanOrEqualTo(1);
        }

        PoolMetrics returnedMetrics = PoolMetrics.from(poolDataSource);
        assertThat(returnedMetrics.statisticsAvailable()).isTrue();
        assertThat(returnedMetrics.borrowedConnections()).isZero();
        assertThat(returnedMetrics.availableConnections()).isGreaterThanOrEqualTo(1);
        assertThat(returnedMetrics.cumulativeConnectionReturnedCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void reportsPoolDiagnostics() throws Exception {
        PoolDataSource poolDataSource = dataSource.unwrap(PoolDataSource.class);
        queryOne(poolDataSource);

        PoolDiagnostics diagnostics = PoolDiagnostics.from(poolDataSource);

        assertThat(diagnostics.connectionPoolName()).isEqualTo("spring-boot-ucp");
        assertThat(diagnostics.registeredPoolNames()).contains("spring-boot-ucp");
        assertThat(diagnostics.metricUpdateInterval()).isGreaterThan(0);
        assertThat(diagnostics.diagnosticTraceEnabled()).isNotBlank();
        assertThat(diagnostics.diagnosticLoggingEnabled()).isNotBlank();
        assertThat(diagnostics.diagnosticBufferSize()).isNotBlank();
        assertThat(diagnostics.diagnosticLoggingLevel()).isNotBlank();
    }

    @Test
    void bindsSizingProfile() {
        assertProfile("sizing", report -> {
            assertThat(report.initialPoolSize()).isEqualTo(2);
            assertThat(report.minPoolSize()).isEqualTo(2);
            assertThat(report.minIdle()).isEqualTo(1);
            assertThat(report.maxPoolSize()).isEqualTo(8);
        });
    }

    @Test
    void dynamicResizingProfileChangesPoolLimitsWithDataSourceSetters() throws SQLException {
        try (ConfigurableApplicationContext context = profileContext("dynamic-resizing", oracle.getJdbcUrl())) {
            PoolDataSource poolDataSource = context.getBean(DataSource.class).unwrap(PoolDataSource.class);
            DynamicPoolResizingService resizingService = context.getBean(DynamicPoolResizingService.class);
            DynamicPoolResizingReport report = resizingService.lastReport();

            assertThat(report.initialMinPoolSize()).isEqualTo(1);
            assertThat(report.initialMaxPoolSize()).isEqualTo(2);
            assertThat(report.expandedMinPoolSize()).isEqualTo(2);
            assertThat(report.expandedMaxPoolSize()).isEqualTo(5);
            assertThat(report.borrowedConnectionsAtExpandedMax()).isEqualTo(5);
            assertThat(report.resizedMinPoolSize()).isEqualTo(1);
            assertThat(report.resizedMaxPoolSize()).isEqualTo(3);
            assertThat(poolDataSource.getMinPoolSize()).isEqualTo(1);
            assertThat(poolDataSource.getMaxPoolSize()).isEqualTo(3);
        }
    }

    @Test
    void bindsStaticSizingProfile() {
        assertProfile("static-sizing", report -> {
            assertThat(report.initialPoolSize()).isEqualTo(3);
            assertThat(report.minPoolSize()).isEqualTo(3);
            assertThat(report.maxPoolSize()).isEqualTo(4);
        });
    }

    @Test
    void bindsTimeoutsProfile() {
        assertProfile("timeouts", report -> {
            assertThat(report.connectionWaitTimeout()).isEqualTo(2);
            assertThat(report.inactiveConnectionTimeout()).isEqualTo(30);
            assertThat(report.connectionValidationTimeout()).isEqualTo(5);
            assertThat(report.timeoutCheckInterval()).isEqualTo(10);
            assertThat(report.maxConnectionReuseTime()).isEqualTo(60);
            assertThat(report.maxConnectionReuseCount()).isEqualTo(25);
        });
    }

    @Test
    void bindsAbandonedProfile() {
        assertProfile("abandoned", report -> {
            assertThat(report.abandonedConnectionTimeout()).isEqualTo(10);
            assertThat(report.timeToLiveConnectionTimeout()).isEqualTo(60);
            assertThat(report.queryTimeout()).isEqualTo(15);
        });
    }

    @Test
    void bindsHarvestingProfileAndCanMarkAConnectionHarvestable() throws SQLException {
        assertProfile("harvesting", report -> {
            assertThat(report.connectionHarvestTriggerCount()).isEqualTo(1);
            assertThat(report.connectionHarvestMaxCount()).isEqualTo(2);
        });

        assertThat(harvestingService.borrowNonHarvestableConnectionForWork()).isEqualTo(1);
    }

    @Test
    void bindsStatementCacheProfile() {
        assertProfile("statement-cache", report -> assertThat(report.maxStatements()).isEqualTo(10));
    }

    @Test
    void bindsValidationProfile() {
        assertProfile("validation", report -> {
            assertThat(report.validateConnectionOnBorrow()).isTrue();
            assertThat(report.secondsToTrustIdleConnection()).isEqualTo(30);
        });
    }

    @Test
    void borrowsLabeledConnectionWithTransactionIsolationState() throws SQLException {
        LabeledConnectionReport report = connectionLabelingService.runSerializableQuery();

        assertThat(report.queryResult()).isEqualTo(1);
        assertThat(report.transactionIsolation()).isEqualTo(Connection.TRANSACTION_SERIALIZABLE);
        assertThat(report.labels())
                .containsEntry(
                        ConnectionLabelingService.TRANSACTION_ISOLATION_LABEL,
                        String.valueOf(Connection.TRANSACTION_SERIALIZABLE)
                );
        assertThat(report.unmatchedLabels()).isEmpty();
    }

    @Test
    void bindsDrcpProfileAndConnectsThroughPooledServer() throws SQLException {
        startDrcp();
        String pooledUrl = rootJdbcUrl() + ":POOLED";

        try (ConfigurableApplicationContext context = profileContext("drcp", pooledUrl, "system", SYS_PASSWORD)) {
            PoolDataSource poolDataSource = context.getBean(DataSource.class).unwrap(PoolDataSource.class);
            PoolReport report = PoolReport.from(poolDataSource);

            assertThat(report.connectionPoolName()).isEqualTo("spring-boot-ucp-drcp");
            assertThat(report.connectionProperties())
                    .containsEntry("oracle.jdbc.DRCPConnectionClass", "spring-boot-ucp");
            assertThat(queryOne(poolDataSource)).isEqualTo(1);
        }
    }

    private void assertProfile(String profile, Consumer<PoolReport> assertions) {
        try (ConfigurableApplicationContext context = profileContext(profile, oracle.getJdbcUrl())) {
            PoolDataSource poolDataSource = context.getBean(DataSource.class).unwrap(PoolDataSource.class);
            PoolReport report = PoolReport.from(poolDataSource);

            assertions.accept(report);
        } catch (SQLException e) {
            throw new AssertionError("Could not inspect UCP profile " + profile, e);
        }
    }

    private ConfigurableApplicationContext profileContext(String profile, String jdbcUrl) {
        return profileContext(profile, jdbcUrl, APP_USERNAME, APP_PASSWORD);
    }

    private ConfigurableApplicationContext profileContext(String profile, String jdbcUrl, String username, String password) {
        return new SpringApplicationBuilder(UcpApplication.class)
                .profiles(profile)
                .properties(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.username=" + username,
                        "spring.datasource.password=" + password,
                        "spring.datasource.oracleucp.u-r-l=" + jdbcUrl,
                        "spring.datasource.oracleucp.user=" + username,
                        "spring.datasource.oracleucp.password=" + password
                )
                .run();
    }

    private int queryOne(DataSource queryDataSource) throws SQLException {
        try (Connection connection = queryDataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("select 1 from dual")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private void startDrcp() throws SQLException {
        OracleDataSource sysDataSource = new OracleDataSource();
        sysDataSource.setURL(rootJdbcUrl());
        sysDataSource.setUser("sys");
        sysDataSource.setPassword(SYS_PASSWORD);
        sysDataSource.setConnectionProperty("internal_logon", "sysdba");

        try (Connection connection = sysDataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                    begin
                        dbms_connection_pool.configure_pool(
                            pool_name => 'SYS_DEFAULT_CONNECTION_POOL',
                            minsize => 1,
                            maxsize => 4,
                            incrsize => 1,
                            session_cached_cursors => 20
                        );
                        dbms_connection_pool.start_pool();
                    end;
                    """);
        }
    }

    private String rootJdbcUrl() {
        return oracle.getJdbcUrl().replace("/freepdb1", "/FREE");
    }
}
