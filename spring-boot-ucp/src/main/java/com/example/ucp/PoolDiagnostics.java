package com.example.ucp;

import java.util.Arrays;
import java.util.List;

import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;

public record PoolDiagnostics(
        String connectionPoolName,
        boolean jmxEnabled,
        int metricUpdateInterval,
        String managerLogLevel,
        String diagnosticTraceEnabled,
        String diagnosticLoggingEnabled,
        String diagnosticBufferSize,
        String diagnosticLoggingLevel,
        String diagnosticErrorCodesToWatchList,
        List<String> registeredPoolNames
) {
    private static final String UNSET = "<not set>";

    public static PoolDiagnostics from(PoolDataSource dataSource) throws UniversalConnectionPoolException {
        UniversalConnectionPoolManager manager = UniversalConnectionPoolManagerImpl
                .getUniversalConnectionPoolManager(dataSource);

        return new PoolDiagnostics(
                dataSource.getConnectionPoolName(),
                manager.isJmxEnabled(),
                manager.getMetricUpdateInterval(),
                manager.getLogLevel().getName(),
                systemProperty(PoolDataSource.SYSTEM_PROPERTY_DIAGNOSTIC_ENABLE_TRACE),
                systemProperty(PoolDataSource.SYSTEM_PROPERTY_DIAGNOSTIC_ENABLE_LOGGING),
                systemProperty(PoolDataSource.SYSTEM_PROPERTY_DIAGNOSTIC_BUFFER_SIZE),
                systemProperty(PoolDataSource.SYSTEM_PROPERTY_DIAGNOSTIC_LOGGING_LEVEL),
                systemProperty(PoolDataSource.SYSTEM_PROPERTY_DIAGNOSTIC_ERROR_CODES_TO_WATCH_LIST),
                Arrays.asList(manager.getConnectionPoolNames())
        );
    }

    private static String systemProperty(String name) {
        return System.getProperty(name, UNSET);
    }
}
