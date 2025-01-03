package com.example.reusable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

public class ReusableVersionTest extends ResuableDatabaseTest {
    @Test
    void getVersion() throws SQLException {
        // Query Database version to verify connection
        try (Connection conn = ResuableDatabaseTest.ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("select * from v$version");
        }
    }
}
