package com.example.txeventq;

import java.util.Scanner;
import java.util.function.Consumer;

public class Prompt implements Runnable {
    private final Consumer<String> promptConsumer;


    public Prompt(Consumer<String> promptConsumer) {
        this.promptConsumer = promptConsumer;
    }


    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");

            String ln = scanner.nextLine();
            if (ln.isEmpty()) {
                continue;
            }
            if (ln.equals("exit")) {
                return;
            }
            if (ln.startsWith("series")) {
                int series = Integer.valueOf(ln.split("series ")[1]);
                for (int i = 0; i < series; i++) {
                    promptConsumer.accept("message #" + (i+1));
                }
                System.out.printf("Accepted %d series messages%n", series);
            } else {
                promptConsumer.accept(ln);
            }
        }
    }

    public static void prompt(Consumer<String> promptConsumer) {
        new Prompt(promptConsumer).run();
    }
}
