package com.example.ucp;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import oracle.ucp.jdbc.HarvestableConnection;
import org.springframework.stereotype.Service;

@Service
public class HarvestingService {
    private final DataSource dataSource;

    public HarvestingService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int borrowNonHarvestableConnectionForWork() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            HarvestableConnection harvestableConnection = (HarvestableConnection) connection;
            harvestableConnection.setConnectionHarvestable(false);
            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("select 1 from dual")) {
                resultSet.next();
                return resultSet.getInt(1);
            } finally {
                harvestableConnection.setConnectionHarvestable(true);
            }
        }
    }
}
