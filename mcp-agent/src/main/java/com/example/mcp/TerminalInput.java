package com.example.mcp;

import dev.langchain4j.agentic.UntypedAgent;

import java.util.Map;
import java.util.Scanner;

public class TerminalInput implements Runnable {
    private final UntypedAgent agent;

    public TerminalInput(UntypedAgent agent) {
        this.agent = agent;
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter text (type 'exit' to quit):");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine();

            if (input.trim().isEmpty()) {
                continue;
            }

            if ("exit".equalsIgnoreCase(input)) {
                System.out.println("Goodbye!");
                break;
            }

            System.out.println("########### PROCESSING ###########");
            Object result = agent.invoke(Map.of(
                    "queryText", input,
                    "dbConnection", "cline_mcp"
            ));
            System.out.println(result);
        }
        scanner.close();
    }
}
