package com.example.txn;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.sql.DataSource;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceImpl;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

public class SessionlessTransactions {

    private static DataSource getDataSource() throws SQLException {
        PoolDataSource ds = new PoolDataSourceImpl();
        ds.setURL("jdbc:oracle:thin:@localhost:1521/freepdb1");
        ds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        ds.setConnectionPoolName("SessionlessTransactionsExample");
        ds.setUser("testuser");
        ds.setPassword("testpwd");
        ds.setMaxPoolSize(30);
        ds.setInitialPoolSize(10);
        ds.setMinPoolSize(1);
        return ds;
    }

    public static void main(String[] args) throws Exception {
        // Get a datasource to a local container database
        DataSource ds = getDataSource();
        OrderService orderService = new OrderService(ds);

        // Start 10 order processing tasks
        ExecutorService executor = newVirtualThreadPerTaskExecutor();
        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            System.out.println("Starting Order " + i);
            tasks.add(executor.submit(orderService::processOrder));
        }
        // Wait for order processing to complete
        for (Future<?> task : tasks) {
            task.get();
        }
        System.out.println("Orders processed");
    }
}
