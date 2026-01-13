package com.example.propagation;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

public class DataSourceFactory {
    public static DataSource create(String username, int port) {
        PoolDataSource ds = PoolDataSourceFactory.getPoolDataSource();

        try {
            ds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
            ds.setConnectionPoolName(UUID.randomUUID().toString());
            ds.setURL(String.format("jdbc:oracle:thin:@localhost:%d/freepdb1", port));
            ds.setUser(username);
            ds.setPassword("testpwd");
            ds.setConnectionPoolName(UUID.randomUUID().toString());
            ds.setMaxPoolSize(30);
            ds.setInitialPoolSize(10);
            ds.setMinPoolSize(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return ds;
    }
}
