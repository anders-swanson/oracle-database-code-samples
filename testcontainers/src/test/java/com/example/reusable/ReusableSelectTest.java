package com.example.reusable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReusableSelectTest extends ResuableDatabaseTest {
    @Test
    public void testSimpleQuery() throws SQLException {
        try (Connection connection = ResuableDatabaseTest.ds.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select 1")) {
            Assertions.assertTrue(rs.next(), "Result set should have at least one row");
            Assertions.assertEquals(1, rs.getInt(1), "Query result should be 1");
        }
    }
}
