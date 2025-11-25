package com.example.mcp;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;

import java.util.List;

public class SQLclMCPToolProvider {
    /**
     * Creates a McpToolProvider for a SQLcl MCP client
     * using the Stdio transport protocol.
     * @return A configured McpToolProvider.
     */
    public static McpToolProvider create() {
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of("sql", "-mcp"))
                .logEvents(true) // only if you want to see the traffic in the log
                .build();

        McpClient client = new DefaultMcpClient.Builder()
                .key("SQLclClient")
                .transport(transport)
                .build();

        return McpToolProvider.builder()
                .mcpClients(client)
                .build();
    }
}
