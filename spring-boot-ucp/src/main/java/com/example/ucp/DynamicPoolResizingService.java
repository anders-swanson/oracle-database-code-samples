package com.example.ucp;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import oracle.ucp.jdbc.PoolDataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dynamic-resizing")
public class DynamicPoolResizingService implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicPoolResizingService.class);

    private final DataSource dataSource;
    private DynamicPoolResizingReport lastReport;

    public DynamicPoolResizingService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        lastReport = resizePool();
    }

    public DynamicPoolResizingReport lastReport() {
        return lastReport;
    }

    public DynamicPoolResizingReport resizePool() throws SQLException {
        PoolDataSource poolDataSource = dataSource.unwrap(PoolDataSource.class);
        queryOne(poolDataSource);

        int initialMinPoolSize = poolDataSource.getMinPoolSize();
        int initialMaxPoolSize = poolDataSource.getMaxPoolSize();

        poolDataSource.setMinPoolSize(2);
        poolDataSource.setMaxPoolSize(5);

        int borrowedConnectionsAtExpandedMax = borrowConnections(poolDataSource, poolDataSource.getMaxPoolSize());

        poolDataSource.setMinPoolSize(1);
        poolDataSource.setMaxPoolSize(3);

        DynamicPoolResizingReport report = new DynamicPoolResizingReport(
                initialMinPoolSize,
                initialMaxPoolSize,
                2,
                5,
                borrowedConnectionsAtExpandedMax,
                poolDataSource.getMinPoolSize(),
                poolDataSource.getMaxPoolSize()
        );
        LOGGER.info("Oracle UCP dynamic pool resizing: {}", report);
        return report;
    }

    private int borrowConnections(PoolDataSource poolDataSource, int connectionCount) throws SQLException {
        List<Connection> connections = new ArrayList<>();
        try {
            for (int i = 0; i < connectionCount; i++) {
                connections.add(poolDataSource.getConnection());
            }
            return PoolMetrics.from(poolDataSource).borrowedConnections();
        } finally {
            for (Connection connection : connections) {
                connection.close();
            }
        }
    }

    private int queryOne(DataSource queryDataSource) throws SQLException {
        try (Connection connection = queryDataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("select 1 from dual")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
