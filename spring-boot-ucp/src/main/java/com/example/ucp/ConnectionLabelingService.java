package com.example.ucp;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import oracle.ucp.ConnectionLabelingCallback;
import oracle.ucp.jdbc.LabelableConnection;
import oracle.ucp.jdbc.PoolDataSource;
import org.springframework.stereotype.Service;

@Service
public class ConnectionLabelingService {
    static final String TRANSACTION_ISOLATION_LABEL = "TRANSACTION_ISOLATION";

    private final PoolDataSource poolDataSource;

    public ConnectionLabelingService(DataSource dataSource) throws SQLException {
        this.poolDataSource = dataSource.unwrap(PoolDataSource.class);
        this.poolDataSource.registerConnectionLabelingCallback(new TransactionIsolationLabelingCallback());
    }

    public LabeledConnectionReport runSerializableQuery() throws SQLException {
        Properties requestedLabels = new Properties();
        requestedLabels.setProperty(
                TRANSACTION_ISOLATION_LABEL,
                String.valueOf(Connection.TRANSACTION_SERIALIZABLE)
        );

        try (Connection connection = poolDataSource.getConnection(requestedLabels);
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("select 1 from dual")) {
            LabelableConnection labelableConnection = (LabelableConnection) connection;

            resultSet.next();
            return new LabeledConnectionReport(
                    resultSet.getInt(1),
                    connection.getTransactionIsolation(),
                    emptyIfNull(labelableConnection.getConnectionLabels()),
                    emptyIfNull(labelableConnection.getUnmatchedConnectionLabels(requestedLabels))
            );
        }
    }

    private static Properties emptyIfNull(Properties properties) {
        return properties == null ? new Properties() : properties;
    }

    private static class TransactionIsolationLabelingCallback implements ConnectionLabelingCallback {
        @Override
        public int cost(Properties requestedLabels, Properties currentLabels) {
            int cost = 0;
            for (Map.Entry<Object, Object> requestedLabel : requestedLabels.entrySet()) {
                Object currentValue = currentLabels.get(requestedLabel.getKey());
                if (!requestedLabel.getValue().equals(currentValue)) {
                    cost++;
                }
            }
            return cost;
        }

        @Override
        public boolean configure(Properties requestedLabels, Object connection) {
            try {
                Connection jdbcConnection = (Connection) connection;
                LabelableConnection labelableConnection = (LabelableConnection) connection;
                String isolationLevel = requestedLabels.getProperty(TRANSACTION_ISOLATION_LABEL);

                if (isolationLevel != null) {
                    jdbcConnection.setTransactionIsolation(Integer.parseInt(isolationLevel));
                }

                Properties unmatchedLabels = labelableConnection.getUnmatchedConnectionLabels(requestedLabels);
                for (Map.Entry<Object, Object> label : unmatchedLabels.entrySet()) {
                    labelableConnection.applyConnectionLabel((String) label.getKey(), (String) label.getValue());
                }
                return true;
            } catch (SQLException | NumberFormatException e) {
                return false;
            }
        }
    }
}
