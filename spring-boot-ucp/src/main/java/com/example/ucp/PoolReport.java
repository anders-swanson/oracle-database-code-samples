package com.example.ucp;

import java.sql.SQLException;
import java.util.Properties;

import oracle.ucp.jdbc.PoolDataSource;

public record PoolReport(
        String connectionPoolName,
        int initialPoolSize,
        int minPoolSize,
        int minIdle,
        int maxPoolSize,
        int connectionWaitTimeout,
        int inactiveConnectionTimeout,
        int connectionValidationTimeout,
        int timeoutCheckInterval,
        long maxConnectionReuseTime,
        int maxConnectionReuseCount,
        int abandonedConnectionTimeout,
        int timeToLiveConnectionTimeout,
        int queryTimeout,
        int connectionHarvestTriggerCount,
        int connectionHarvestMaxCount,
        int maxStatements,
        boolean validateConnectionOnBorrow,
        int secondsToTrustIdleConnection,
        Properties connectionProperties
) {

    public static PoolReport from(PoolDataSource dataSource) throws SQLException {
        return new PoolReport(
                dataSource.getConnectionPoolName(),
                dataSource.getInitialPoolSize(),
                dataSource.getMinPoolSize(),
                dataSource.getMinIdle(),
                dataSource.getMaxPoolSize(),
                dataSource.getConnectionWaitTimeout(),
                dataSource.getInactiveConnectionTimeout(),
                dataSource.getConnectionValidationTimeout(),
                dataSource.getTimeoutCheckInterval(),
                dataSource.getMaxConnectionReuseTime(),
                dataSource.getMaxConnectionReuseCount(),
                dataSource.getAbandonedConnectionTimeout(),
                dataSource.getTimeToLiveConnectionTimeout(),
                dataSource.getQueryTimeout(),
                dataSource.getConnectionHarvestTriggerCount(),
                dataSource.getConnectionHarvestMaxCount(),
                dataSource.getMaxStatements(),
                dataSource.getValidateConnectionOnBorrow(),
                dataSource.getSecondsToTrustIdleConnection(),
                dataSource.getConnectionProperties()
        );
    }
}
