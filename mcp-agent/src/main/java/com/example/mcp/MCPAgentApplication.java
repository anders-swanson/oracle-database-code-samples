package com.example.mcp;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;

public class MCPAgentApplication {
    public static void main(String[] args) {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-5-nano")
                .build();

        AgentListener agentCompletedLogger = new AgentListener() {
            @Override
            public void afterAgentInvocation(AgentResponse agentResponse) {
                System.out.printf("### Agent %s completed ###", agentResponse.agentName());
            }
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
                .listener(agentCompletedLogger)
                .toolProvider(SQLclMCPToolProvider.create())
                .outputKey("queryResults")
                .build();

        SQLSummaryAgent sqlSummaryAgent = AgenticServices.agentBuilder(SQLSummaryAgent.class)
                .chatModel(chatModel)
                .listener(agentCompletedLogger)
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
