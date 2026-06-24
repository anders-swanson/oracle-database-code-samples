package com.example.ucp;

public record DynamicPoolResizingReport(
        int initialMinPoolSize,
        int initialMaxPoolSize,
        int expandedMinPoolSize,
        int expandedMaxPoolSize,
        int borrowedConnectionsAtExpandedMax,
        int resizedMinPoolSize,
        int resizedMaxPoolSize
) {
}
