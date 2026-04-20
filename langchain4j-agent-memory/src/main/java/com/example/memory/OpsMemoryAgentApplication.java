package com.example.memory;

import com.example.memory.agent.MemoryTools;
import com.example.memory.agent.OpsMemoryAssistant;
import com.example.memory.search.MemoryRepository;
import com.example.memory.transcript.ConversationTranscriptRepository;
import com.example.memory.transcript.LoggingChatMemory;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import oracle.jdbc.datasource.impl.OracleDataSource;

import java.sql.SQLException;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OpsMemoryAgentApplication {
    private static final long SPINNER_DELAY_MILLIS = 100L;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: <jdbcUrl> <username> <password>");
            System.exit(1);
        }

        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY must be set for the live agent demo.");
        }

        OracleDataSource dataSource = createDataSource(args[0], args[1], args[2]);
        String conversationId = UUID.randomUUID().toString();
        MemoryRepository repository = new MemoryRepository(dataSource, new EmbeddingClient(apiKey));
        ConversationTranscriptRepository transcriptRepository = new ConversationTranscriptRepository(dataSource);
        repository.initializeSchema();
        transcriptRepository.initializeSchema();
        repository.seedIfEmpty(SeedMemories.records());

        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-5-nano")
                .build();

        Executor transcriptExecutor = command -> Thread.ofVirtual().start(command);
        ChatMemory chatMemory = new LoggingChatMemory(
                MessageWindowChatMemory.builder()
                        .id(conversationId)
                        .maxMessages(20)
                        .build(),
                conversationId,
                transcriptRepository,
                transcriptExecutor
        );

        OpsMemoryAssistant assistant = AiServices.builder(OpsMemoryAssistant.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .tools(new MemoryTools(repository))
                .build();

        System.out.println("Oracle AI Database Agent Memory Demo");
        System.out.println("Conversation ID: " + conversationId);
        System.out.println("Try:");
        System.out.println("- What happened during the checkout incident after CHG2145?");
        System.out.println("- Which runbook section should I use for the checkout rollback?");
        System.out.println("- Draft a next-shift handoff and remember it.");
        System.out.println("Type 'exit' to quit.");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input.trim())) {
                    break;
                }
                TerminalSpinner spinner = new TerminalSpinner("Agent working");
                spinner.start();
                try {
                    String response = assistant.chat(input);
                    System.out.println(response);
                } catch (RuntimeException e) {
                    System.err.println("Agent request failed: " + e.getMessage());
                } finally {
                    spinner.stop();
                }
            }
        }
    }

    private static OracleDataSource createDataSource(String url, String username, String password) throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private static final class TerminalSpinner {
        private static final char[] FRAMES = {'|', '/', '-', '\\'};

        private final String label;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private Thread thread;

        private TerminalSpinner(String label) {
            this.label = label;
        }

        private void start() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            thread = Thread.ofVirtual().start(() -> {
                int index = 0;
                while (running.get()) {
                    System.out.print("\r" + label + " " + FRAMES[index % FRAMES.length]);
                    System.out.flush();
                    index++;
                    try {
                        Thread.sleep(SPINNER_DELAY_MILLIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        private void stop() {
            if (!running.compareAndSet(true, false)) {
                return;
            }
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.print("\r" + " ".repeat(label.length() + 2) + "\r");
            System.out.flush();
        }
    }
}
