package com.example.okafkamemory.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;

/** Creates clients that use a caller-owned connection without closing it. */
public final class SingleConnectionJdbcClientFactory {
    private SingleConnectionJdbcClientFactory() {
    }

    public static JdbcClient create(Connection connection) {
        return JdbcClient.create(new SingleConnectionDataSource(connection, true));
    }
}
