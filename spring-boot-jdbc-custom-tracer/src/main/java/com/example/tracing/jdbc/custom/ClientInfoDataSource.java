package com.example.tracing.jdbc.custom;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class ClientInfoDataSource extends DelegatingDataSource {
    private final Properties clientInfo;

    public ClientInfoDataSource(DataSource original, Properties clientInfo) {
        super(original);
        this.clientInfo = clientInfo;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        connection.setClientInfo(clientInfo);
        return connection;
    }
}
