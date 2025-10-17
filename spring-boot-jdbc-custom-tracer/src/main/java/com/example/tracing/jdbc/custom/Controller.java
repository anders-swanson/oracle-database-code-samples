package com.example.tracing.jdbc.custom;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@RestController
public class Controller {
    private final DataSource dataSource;

    private static final String MODULE = Controller.class.getName();
    private static final String ACTION_GET_ALL = "Get all ice cream flavors";
    private static final String ACTION_GET_ONE = "Get one ice cream flavor";
    private static final String ACTION_CREATE = "Create ice cream flavor";

    public Controller(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record IceCreamFlavor(long id, String flavor) {}

    private final RowMapper<IceCreamFlavor> rowMapper = (rs, rowNum) ->
            new IceCreamFlavor(
                    rs.getLong("id"),
                    rs.getString("flavor")
            );

    @GetMapping("/flavors")
    public List<IceCreamFlavor> getIceCreamFlavors() throws SQLException {
        List<IceCreamFlavor> flavors = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("select * from ice_cream_flavors")) {

            prepareConnection(conn, ACTION_GET_ALL);
            int rowNum = 0;
            while (rs.next()) {
                flavors.add(rowMapper.mapRow(rs, rowNum++));
            }
        }
        return flavors;
    }

    @PostMapping("/flavors")
    public IceCreamFlavor createIceCreamFlavor(@RequestBody IceCreamFlavor iceCreamFlavor) throws SQLException {
        long generatedId;

        // --- Insert and capture generated key
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO ice_cream_flavors (flavor) VALUES (?)", new String[]{"id",})) {

            prepareConnection(conn, ACTION_CREATE);
            ps.setString(1, iceCreamFlavor.flavor());
            ps.executeUpdate ();

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    generatedId = generatedKeys.getLong(1);
                } else {
                    throw new SQLException("Insert failed: no ID obtained.");
                }
            }
        }

        // --- Query and return inserted row
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM ice_cream_flavors WHERE id = ?")) {

            prepareConnection(conn, ACTION_GET_ONE);
            ps.setLong(1, generatedId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowMapper.mapRow(rs, 0);
                } else {
                    throw new SQLException("Inserted flavor not found with id " + generatedId);
                }
            }
        }
    }

    private void prepareConnection(Connection conn, String action) throws SQLClientInfoException {
        conn.setClientInfo("OCSID.MODULE", MODULE);
        conn.setClientInfo("OCSID.ACTION", action);
    }
}
