package com.example.ucp;

import oracle.ucp.UniversalConnectionPoolStatistics;
import oracle.ucp.jdbc.PoolDataSource;

public record PoolMetrics(
        boolean statisticsAvailable,
        int totalConnections,
        int availableConnections,
        int borrowedConnections,
        int peakConnections,
        int peakBorrowedConnections,
        int remainingPoolCapacity,
        int pendingRequests,
        int connectionsCreated,
        int connectionsClosed,
        long averageConnectionWaitTime,
        long peakConnectionWaitTime,
        long cumulativeConnectionBorrowedCount,
        long cumulativeConnectionReturnedCount,
        long cumulativeConnectionUseTime
) {

    public static PoolMetrics from(PoolDataSource dataSource) {
        UniversalConnectionPoolStatistics statistics = dataSource.getStatistics();

        // UCP is lazily initialized, and the statistics object is created only after the
        // pool is started, typically when the first connection is borrowed.
        if (statistics == null) {
            return empty();
        }
        return new PoolMetrics(
                true,
                statistics.getTotalConnectionsCount(),
                statistics.getAvailableConnectionsCount(),
                statistics.getBorrowedConnectionsCount(),
                statistics.getPeakConnectionsCount(),
                statistics.getPeakBorrowedConnectionsCount(),
                statistics.getRemainingPoolCapacityCount(),
                statistics.getPendingRequestsCount(),
                statistics.getConnectionsCreatedCount(),
                statistics.getConnectionsClosedCount(),
                statistics.getAverageConnectionWaitTime(),
                statistics.getPeakConnectionWaitTime(),
                statistics.getCumulativeConnectionBorrowedCount(),
                statistics.getCumulativeConnectionReturnedCount(),
                statistics.getCumulativeConnectionUseTime()
        );
    }

    private static PoolMetrics empty() {
        return new PoolMetrics(
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }
}
