package com.harsh.cloudsim.agent.event;

import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;

import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Interactive command-line program for manually testing the
 * Gemini-powered Event Intelligence Agent.
 */
public final class EventAgentCliRunner {

    private static final String USER_ID = "puranjay";
    private static final String QUIT_COMMAND = "quit";

    private EventAgentCliRunner() {
    }

    public static void main(String[] args) {
        verifyApiKey();

        RunConfig runConfig =
                RunConfig.builder().build();

        InMemoryRunner runner =
                new InMemoryRunner(
                        EventIntelligenceAgent.ROOT_AGENT
                );

        Session session = runner
                .sessionService()
                .createSession(
                        runner.appName(),
                        USER_ID
                )
                .blockingGet();

        System.out.println(
                "Event Intelligence Agent is ready."
        );

        System.out.println(
                "Describe an event, or enter 'quit' to stop."
        );

        try (Scanner scanner = new Scanner(System.in, UTF_8)) {
            while (true) {
                System.out.print("\nEvent > ");

                String userInput = scanner.nextLine().trim();

                if (QUIT_COMMAND.equalsIgnoreCase(userInput)) {
                    System.out.println("Event agent stopped.");
                    break;
                }

                if (userInput.isBlank()) {
                    System.out.println(
                            "Please enter an event description."
                    );
                    continue;
                }

                Content userMessage = Content.fromParts(
                        Part.fromText(userInput)
                );

                Flowable<Event> events = runner.runAsync(
                        session.userId(),
                        session.id(),
                        userMessage,
                        runConfig
                );

                System.out.println("\nAgent assessment:");

                events.blockingForEach(event -> {
                    if (event.finalResponse()) {
                        System.out.println(
                                event.stringifyContent()
                        );
                    }
                });
            }
        }
    }

    /**
     * Fails early with a clear message instead of sending an
     * unauthenticated request to Gemini.
     */
    private static void verifyApiKey() {
        String apiKey = System.getenv("GOOGLE_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_API_KEY is not configured."
            );
        }
    }
}