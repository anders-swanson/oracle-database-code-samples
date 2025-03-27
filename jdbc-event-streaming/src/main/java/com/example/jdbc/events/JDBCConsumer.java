package com.example.jdbc.events;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;

import javax.sql.DataSource;
import oracle.jdbc.OracleTypes;

public class JDBCConsumer implements Runnable {
    // Ignore errors empty queue errors.
    private static final int END_OF_FETCH_CODE = 25228;

    private final DataSource dataSource;
    private final int id;
    private final CountDownLatch expectedEvents;

    public JDBCConsumer(DataSource dataSource, int id, CountDownLatch expectedEvents) {
        this.dataSource = dataSource;
        this.id = id;
        this.expectedEvents = expectedEvents;
    }

    @Override
    public void run() {
        while (expectedEvents.getCount() > 0) {
            try (Connection conn = dataSource.getConnection();
                 CallableStatement cs = conn.prepareCall("{? = call consume_json_event()}");
                 ) {

                // Consume the event.
                cs.registerOutParameter(1, OracleTypes.JSON);
                cs.executeUpdate();

                // Save the event into the weather_events table.
                byte[] oson = cs.getBytes(1);
                if (oson != null) {
                    try (PreparedStatement ps = conn.prepareStatement("insert into weather_events (data) values(?)")) {
                        ps.setObject(1, oson, OracleTypes.JSON);
                        ps.execute();
                        expectedEvents.countDown();
                    }
                }
                Thread.sleep(1000);
            } catch (SQLException e) {
                if (e.getErrorCode() != END_OF_FETCH_CODE) {
                    System.err.println("Error consuming event: " + e.getMessage());
                }
            } catch (InterruptedException e) {
                System.err.println("Timeout consuming event: " + e.getMessage());
            }
        }
        System.out.printf("[CONSUMER %d] All events consumed. Shutting down consumer.\n", id);
    }
}
