package com.example.nl2sql;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

public class SelectAI {
    private final String profile;

    public SelectAI(String profile) {
        this.profile = profile;
    }

    public String call(Connection conn, String prompt, Action action) {
        String sql = """
            BEGIN
                ? := DBMS_CLOUD_AI.GENERATE(
                         prompt       => ?,
                         action       => ?,
                         profile_name => ?);
            END;
            """;

        try (CallableStatement statement = conn.prepareCall(sql)) {
            statement.registerOutParameter(1, Types.CLOB);
            statement.setString(2, prompt);
            statement.setString(3, action.getAction());
            statement.setString(4, profile);
            statement.execute();
            return statement.getString(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // runsql, showsql, explainsql, narrate, or chat
    public enum Action {
        RUNSQL("RUNSQL"),
        SHOWSQL("SHOWSQL"),
        EXPLAINSQL("EXPLAINSQL"),
        NARRATE("NARRATE"),
        CHAT("CHAT");

        private final String action;

        Action(String action) {
            this.action = action;
        }

        public String getAction() {
            return action;
        }
    }
}
