package com.example.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class SqlScriptRunner {
    private SqlScriptRunner() {
    }

    static void runResource(Connection connection, String resourcePath) throws IOException, SQLException {
        String script = readResource(resourcePath);
        for (String rawStatement : script.split("(?m)^/\\s*$")) {
            String sql = stripWholeLineComments(rawStatement).trim();
            if (sql.isEmpty()) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    static String readResource(String resourcePath) throws IOException {
        try (InputStream stream = SqlScriptRunner.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String stripWholeLineComments(String sql) {
        return String.join(
                System.lineSeparator(),
                sql.lines()
                        .filter(line -> !line.trim().startsWith("--"))
                        .toList()
        );
    }
}
