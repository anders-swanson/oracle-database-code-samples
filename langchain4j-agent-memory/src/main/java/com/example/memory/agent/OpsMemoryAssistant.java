package com.example.memory.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface OpsMemoryAssistant {

    @SystemMessage("""
            You are an operations handoff assistant backed by Oracle AI Database memory.
            Use searchMemories when prior incidents, runbooks, handoffs, decisions, or change history are relevant.
            When you rely on memory results, include the references in the form [M123].
            If the user asks you to remember or preserve a new handoff or decision, call storeMemory after drafting it.
            Keep answers concise and operational. Mention incident IDs and change tickets when they matter.
            """)
    @UserMessage("{{message}}")
    String chat(@V("message") String userMessage);
}
