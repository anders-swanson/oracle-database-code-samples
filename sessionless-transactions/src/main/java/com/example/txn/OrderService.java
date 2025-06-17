package com.example.txn;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

import javax.sql.DataSource;
import oracle.jdbc.OracleConnection;

public class OrderService {
    private final Random random = new Random();
    private final DataSource dataSource;

    public OrderService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void processOrder() {
        try {
            byte[] gtrid = startOrder();
            if (!validateInventory(gtrid)) {
                return;
            }
            finishOrder(gtrid);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] startOrder() throws SQLException {
        final String sql = "insert into order_processing (status, gtrid) values('created', ?)";
        try (OracleConnection conn = (OracleConnection) dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            // Start a connection with a timeout of 30 seconds (default 60 seconds).
            // The JDBC driver generates a GTRID for us.
            byte[] gtrid = conn.startTransaction(30);
            stmt.setBytes(1, gtrid);
            stmt.executeUpdate();
            conn.suspendTransactionImmediately();
            return gtrid;
        }
    }

    private boolean validateInventory(byte[] gtrid) throws SQLException {
        final String sql = "insert into order_processing (status, gtrid) values(?, ?)";
        try (OracleConnection conn = (OracleConnection) dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            // Resume the transaction using the GTRID.
            conn.resumeTransaction(gtrid);
            boolean isInventoryOK = simulateExternalCall();
            stmt.setString(1, isInventoryOK ? "inventory_reserved" : "failed");
            stmt.setBytes(2, gtrid);
            stmt.executeUpdate();
            if (isInventoryOK) {
                conn.suspendTransactionImmediately();
            } else {
                conn.commit();
            }
            return isInventoryOK;
        }
    }

    private void finishOrder(byte[] gtrid) throws SQLException {
        final String sql = "insert into order_processing (status, gtrid) values('completed', ?)";
        try (OracleConnection conn = (OracleConnection) dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            // Resume the transaction using the GTRID.
            conn.resumeTransaction(gtrid);
            stmt.setBytes(1, gtrid);
            stmt.executeUpdate();
            conn.commit();
        }
    }

    private boolean simulateExternalCall() {
        try {
            // Simulate an external call that waits for some amount of time
            Thread.sleep(random.nextLong(1000, 3000));
            return random.nextInt(100) > 33;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
