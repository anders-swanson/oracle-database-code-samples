# Oracle Database MCP Agent Example

This project demonstrates a natural language SQL agent using Langchain4j, integrated with Oracle Database via SQLcl MCP (Model Context Protocol) for executing queries.

## Prerequisites

- Docker and Docker Compose
- Maven
- Java 21 or higher
- SQLcl (installed and available in PATH for the agent to use internally)

## Setup

### 1. Start the Oracle Database Free container
Use Docker Compose to start the Oracle Database Free container. This will also run the initialization script (`oracle/grant_permissions.sql`) to create the `testuser` schema, grant permissions, create sample tables (players, games, game_sessions), and insert sample data.

```bash
docker compose up -d
```

The database will be available at `localhost:1530/freepdb1` with admin password `Welcome12345`. The sample user is `testuser/testpwd`.

### MCP Startup Commands
The agent internally starts SQLcl in MCP mode using stdio transport with the command:

```bash
sql testuser/testpwd@localhost:1530/freepdb1
```

Save the connection for reuse:

```bash
conn -save cline_mcp -savepwd testuser/testpwd@localhost:1530/freepdb1
```

### 2. Build the Application
Build and run the Java application using Maven:

```bash
mvn compile exec:java
```

This initializes the SQLcl MCP agent and starts a terminal input loop.

### 2. Interact with the Agent

Once running, the application will prompt:

```
Enter text (type 'exit' to quit):
> 
```

Enter a natural language query, e.g.:
- "List the top 10 players by score"
- "Show games released after 2010"
- "What is the average session duration per country?"

The agent translates the query to SQL, executes it via SQLcl MCP, and displays the results.

Type `exit` to stop.

This is handled by the `SQLclMCPToolProvider` class, which configures an MCP client to communicate with SQLcl for query execution. No manual startup is needed; it's managed by the application.

## General Agent Flow
1. **User Input**: Enter a natural language query in the terminal.
2. **Query Interpretation**: The `SQLclMCPAgent` (powered by Langchain4j) analyzes the query and generates valid Oracle SQL.
3. **SQL Execution**: The agent uses the MCP tool to send the SQL to SQLcl (running in MCP mode), which executes it against the connected Oracle Database.
4. **Result Processing**: Results are retrieved and displayed in a human-readable format.

## Troubleshooting
- Ensure the Docker container is healthy (check with `docker ps`).
- If SQLcl is not found, verify it's installed and in your PATH.
- The sample data is in tables `PLAYERS`, `GAMES`, and `GAME_SESSIONS` under `testuser`.

For more details, see the source code in `src/main/java/com/example/mcp/`.
