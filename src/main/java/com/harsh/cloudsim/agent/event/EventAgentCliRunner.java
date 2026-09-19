package com.harsh.cloudsim.agent.event;

import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import io.reactivex.rxjava3.core.Flowable;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Interactive command-line program for manually testing the
 * Gemini-powered Event Intelligence Agent.
 *
 * The runner sends an event description to Gemini, captures the final
 * JSON response, validates it and converts it into a typed Java object.
 */
public final class EventAgentCliRunner {

    private static final String USER_ID = "puranjay";
    private static final String QUIT_COMMAND = "quit";

    private EventAgentCliRunner() {
        // Application entry-point class; instances are unnecessary.
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
            runInteractiveLoop(
                    scanner,
                    runner,
                    session,
                    runConfig
            );
        }
    }

    private static void runInteractiveLoop(
            Scanner scanner,
            InMemoryRunner runner,
            Session session,
            RunConfig runConfig
    ) {
        while (true) {
            System.out.print("\nEvent > ");

            String userInput = scanner.nextLine().trim();

            if (QUIT_COMMAND.equalsIgnoreCase(userInput)) {
                System.out.println("Event agent stopped.");
                return;
            }

            if (userInput.isBlank()) {
                System.out.println(
                        "Please enter an event description."
                );
                continue;
            }

            try {
                EventImpactAssessment assessment =
                        generateAssessment(
                                userInput,
                                runner,
                                session,
                                runConfig
                        );

                printAssessment(assessment);
            } catch (EventAssessmentParsingException exception) {
                System.err.println(
                        "\nThe agent returned an invalid assessment."
                );

                System.err.println(
                        "Reason: " + exception.getMessage()
                );

                System.err.println(
                        "No scaling decision will be made "
                                + "from this response."
                );
            } catch (RuntimeException exception) {
                System.err.println(
                        "\nThe Gemini request could not be completed."
                );

                System.err.println(
                        "Reason: " + readableMessage(exception)
                );

                System.err.println(
                        "You may correct the problem and try again."
                );
            }
        }
    }

    /**
     * Sends the event description to Gemini and converts the final
     * response into a validated EventImpactAssessment.
     */
    private static EventImpactAssessment generateAssessment(
            String userInput,
            InMemoryRunner runner,
            Session session,
            RunConfig runConfig
    ) {
        Content userMessage = Content.fromParts(
                Part.fromText(userInput)
        );

        Flowable<Event> events = runner.runAsync(
                session.userId(),
                session.id(),
                userMessage,
                runConfig
        );

        AtomicReference<String> finalResponse =
                new AtomicReference<>();

        events.blockingForEach(event -> {
            if (event.finalResponse()) {
                finalResponse.set(
                        event.stringifyContent()
                );
            }
        });

        String rawResponse = finalResponse.get();

        if (rawResponse == null || rawResponse.isBlank()) {
            throw new EventAssessmentParsingException(
                    "Gemini did not return a final response."
            );
        }

        return EventAssessmentParser.parse(rawResponse);
    }

    /**
     * Displays the validated Java object in a human-readable format.
     */
    private static void printAssessment(
            EventImpactAssessment assessment
    ) {
        System.out.println(
                "\nValidated event-impact assessment"
        );

        System.out.println(
                "---------------------------------"
        );

        System.out.println(
                "Event name: " + assessment.eventName()
        );

        System.out.println(
                "Event type: " + assessment.eventType()
        );

        System.out.println(
                "Affected service: "
                        + assessment.affectedService()
        );

        System.out.printf(
                "Expected users: %,d%n",
                assessment.expectedUsers()
        );

        System.out.printf(
                "Traffic multiplier: %.2fx%n",
                assessment.trafficMultiplier()
        );

        System.out.printf(
                "Lead time: %,d minutes%n",
                assessment.leadTimeMinutes()
        );

        System.out.printf(
                "Confidence: %.2f%n",
                assessment.confidence()
        );

        System.out.println(
                "Reasoning: " + assessment.reasoning()
        );

        System.out.println(
                "Validation status: ACCEPTED"
        );
    }

    /**
     * Traverses wrapped exceptions to find the most useful message.
     */
    private static String readableMessage(Throwable throwable) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }

        return message;
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