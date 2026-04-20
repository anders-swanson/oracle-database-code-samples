package com.example.memory.transcript;

import oracle.jdbc.OracleTypes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ConversationTranscriptRepository {
    private static final String CREATE_TABLE_SQL = """
            create table if not exists agent_conversation_log (
                id number generated always as identity primary key,
                conversation_id varchar2(100) not null,
                message_seq number not null,
                role varchar2(20) not null,
                message_type varchar2(50) not null,
                message_text clob,
                tool_name varchar2(200),
                tool_call_id varchar2(200),
                is_error number(1),
                context_json json,
                created_at timestamp with time zone default systimestamp not null,
                constraint agent_conversation_log_uq unique (conversation_id, message_seq)
            )
            """;
    private static final String CREATE_CONVERSATION_INDEX_SQL = """
            create index if not exists agent_conversation_log_conv_idx
            on agent_conversation_log (conversation_id, message_seq)
            """;
    private static final String INSERT_SQL = """
            insert into agent_conversation_log (
                conversation_id,
                message_seq,
                role,
                message_type,
                message_text,
                tool_name,
                tool_call_id,
                is_error,
                context_json
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_BY_CONVERSATION_SQL = """
            select id,
                   conversation_id,
                   message_seq,
                   role,
                   message_type,
                   message_text,
                   tool_name,
                   tool_call_id,
                   is_error,
                   json_serialize(context_json returning clob) as context_json_text,
                   created_at
            from agent_conversation_log
            where conversation_id = ?
            order by message_seq, id
            """;

    private final DataSource dataSource;

    public ConversationTranscriptRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    public void initializeSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            execute(connection, CREATE_TABLE_SQL);
            execute(connection, CREATE_CONVERSATION_INDEX_SQL);
            connection.commit();
        }
    }

    public void store(ConversationLogEntry entry) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, entry.conversationId());
            statement.setLong(2, entry.messageSeq());
            statement.setString(3, entry.role());
            statement.setString(4, entry.messageType());
            statement.setString(5, entry.messageText());
            statement.setString(6, entry.toolName());
            statement.setString(7, entry.toolCallId());
            if (entry.error() == null) {
                statement.setNull(8, java.sql.Types.NUMERIC);
            } else {
                statement.setInt(8, entry.error() ? 1 : 0);
            }
            if (entry.contextJson() == null) {
                statement.setNull(9, OracleTypes.JSON);
            } else {
                statement.setObject(9, entry.contextJson(), OracleTypes.JSON);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<ConversationLogEntry> findByConversationId(String conversationId) {
        List<ConversationLogEntry> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_CONVERSATION_SQL)) {
            statement.setString(1, conversationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new ConversationLogEntry(
                            resultSet.getLong("id"),
                            resultSet.getString("conversation_id"),
                            resultSet.getLong("message_seq"),
                            resultSet.getString("role"),
                            resultSet.getString("message_type"),
                            resultSet.getString("message_text"),
                            resultSet.getString("tool_name"),
                            resultSet.getString("tool_call_id"),
                            readNullableBoolean(resultSet, "is_error"),
                            resultSet.getString("context_json_text"),
                            resultSet.getObject("created_at", OffsetDateTime.class)
                    ));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Boolean readNullableBoolean(ResultSet resultSet, String columnName) throws SQLException {
        int raw = resultSet.getInt(columnName);
        if (resultSet.wasNull()) {
            return null;
        }
        return raw != 0;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
