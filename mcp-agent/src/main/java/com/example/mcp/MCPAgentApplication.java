package com.example.mcp;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;

import java.util.function.Consumer;

public class MCPAgentApplication {
    public static void main(String[] args) {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o-mini")
                .build();

        Consumer<AgentResponse> agentCompletedLogger = agentResponse -> {
            System.out.printf("### Agent %s completed ###", agentResponse.agentName());
        };

        // Use an in-memory chat memory
        ChatMemory chatMemory = new MessageWindowChatMemory.Builder()
                .id("12345")
                .maxMessages(10)
                .chatMemoryStore(new InMemoryChatMemoryStore())
                .build();

        SQLclMCPAgent sqLclMCPAgent = AgenticServices.agentBuilder(SQLclMCPAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider((any) -> chatMemory)
                .afterAgentInvocation(agentCompletedLogger)
                .toolProvider(SQLclMCPToolProvider.create())
                .outputKey("queryResults")
                .build();

        SQLSummaryAgent sqlSummaryAgent = AgenticServices.agentBuilder(SQLSummaryAgent.class)
                .chatModel(chatModel)
                .afterAgentInvocation(agentCompletedLogger)
                .outputKey("report")
                .build();

        UntypedAgent topLevelAgent = AgenticServices
                .sequenceBuilder()
                .subAgents(sqLclMCPAgent, sqlSummaryAgent)
                .outputKey("report")
                .build();


        System.out.println("Starting Oracle SQLcl MCP Agent Example");
        new TerminalInput(topLevelAgent).run();
    }
}
