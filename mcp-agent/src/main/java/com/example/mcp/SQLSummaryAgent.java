package com.example.mcp;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SQLSummaryAgent {

    @UserMessage("""
        You are a professional editor who reviews and rewrites results from a SQL MCP server.
        Summarize the provided SQL query results as a text report.
        Prioritize a clean, direct report that identifies key details.
        The SQL query results are "{{queryResults}}".
        """)
    @Agent(outputKey = "report", description = "Rewrites SQL query results as text reports")
    String writeReport(@V("queryResults") String queryResults);
}
