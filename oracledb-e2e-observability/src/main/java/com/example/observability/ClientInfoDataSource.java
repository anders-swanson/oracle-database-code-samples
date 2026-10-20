package com.example.observability;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Properties;
import javax.sql.DataSource;

import oracle.jdbc.OracleConnection;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Applies defaults on every borrow so a pooled session cannot retain a previous client's identity. */
public class ClientInfoDataSource extends DelegatingDataSource {
    private final boolean serverTraces;

    public ClientInfoDataSource(DataSource delegate, boolean serverTraces) {
        super(delegate);
        this.serverTraces = serverTraces;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return decorate(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return decorate(super.getConnection(username, password));
    }

    private Connection decorate(Connection connection) throws SQLException {
        try {
            Properties info = new Properties();
            info.setProperty("OCSID.MODULE", "oracledb-e2e-observability");
            info.setProperty("OCSID.ACTION", "readings.list");
            info.setProperty("OCSID.CLIENTID", "anonymous");
            connection.setClientInfo(info);
            if (serverTraces) {
                OracleConnection oracle = connection.unwrap(OracleConnection.class);
                // Force the driver's telemetry state to be piggybacked to the server.
                oracle.setServerTelemetry(EnumSet.noneOf(OracleConnection.ServerTelemetry.class));
                oracle.setServerTelemetry(EnumSet.of(OracleConnection.ServerTelemetry.Traces));
                // Oracle's server-export example requests trace capture using this client context.
                oracle.setClientInfo("clientcontext.ora$opentelem$tracelevel", "tracelevel: 0x01");
            }
            return connection;
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }
}
