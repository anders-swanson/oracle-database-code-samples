package com.example.cdc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class TicketService {
    private final DataSource dataSource;

    public TicketService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTicket(String title, String status) {
        final String sql = "insert into tickets (title, status) values (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, status);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
