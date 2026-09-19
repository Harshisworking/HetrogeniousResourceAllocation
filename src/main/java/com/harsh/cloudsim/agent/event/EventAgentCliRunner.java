package com.harsh.cloudsim.agent.event;

import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.verification.EventVerificationAgent;
import com.harsh.cloudsim.agent.verification.model.EventVerificationRequest;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import io.reactivex.rxjava3.core.Flowable;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Interactive workflow connecting the Gemini Event Intelligence Agent
 * to the deterministic Event Verification Agent.
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

        EventVerificationAgent verificationAgent =
                new EventVerificationAgent();

        System.out.println(
                "Event Intelligence and Verification workflow is ready."
        );

        System.out.println(
                "Enter 'quit' at any prompt to stop."
        );

        try (Scanner scanner = new Scanner(System.in, UTF_8)) {
            runInteractiveLoop(
                    scanner,
                    runner,
                    session,
                    runConfig,
                    verificationAgent
            );
        }
    }

    private static void runInteractiveLoop(
            Scanner scanner,
            InMemoryRunner runner,
            Session session,
            RunConfig runConfig,
            EventVerificationAgent verificationAgent
    ) {
        while (true) {
            System.out.print("\nEvent description > ");

            String userInput = scanner.nextLine().trim();

            if (isQuitCommand(userInput)) {
                stopAgent();
                return;
            }

            if (userInput.isBlank()) {
                System.out.println(
                        "Please enter an event description."
                );
                continue;
            }

            System.out.print("Source URL/domain > ");

            String sourceReference =
                    scanner.nextLine().trim();

            if (isQuitCommand(sourceReference)) {
                stopAgent();
                return;
            }

            if (sourceReference.isBlank()) {
                System.out.println(
                        "A source URL or domain is required "
                                + "for verification."
                );
                continue;
            }

            processEvent(
                    userInput,
                    sourceReference,
                    runner,
                    session,
                    runConfig,
                    verificationAgent
            );
        }
    }

    private static void processEvent(
            String userInput,
            String sourceReference,
            InMemoryRunner runner,
            Session session,
            RunConfig runConfig,
            EventVerificationAgent verificationAgent
    ) {
        try {
            EventImpactAssessment assessment =
                    generateAssessment(
                            userInput,
                            runner,
                            session,
                            runConfig
                    );

            printAssessment(assessment);

            EventVerificationRequest verificationRequest =
                    new EventVerificationRequest(
                            assessment,
                            sourceReference
                    );

            EventVerificationResult verificationResult =
                    verificationAgent.verify(
                            verificationRequest
                    );

            printVerificationResult(
                    verificationResult
            );
        } catch (EventAssessmentParsingException exception) {
            System.err.println(
                    "\nThe Event Intelligence Agent returned "
                            + "an invalid assessment."
            );

            System.err.println(
                    "Reason: " + exception.getMessage()
            );

            System.err.println(
                    "Safety decision: BLOCKED"
            );
        } catch (RuntimeException exception) {
            System.err.println(
                    "\nThe event workflow could not be completed."
            );

            System.err.println(
                    "Reason: " + readableMessage(exception)
            );

            System.err.println(
                    "Safety decision: BLOCKED"
            );
        }
    }

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
                "LLM confidence: %.2f%n",
                assessment.confidence()
        );

        System.out.println(
                "Reasoning: " + assessment.reasoning()
        );
    }

    private static void printVerificationResult(
            EventVerificationResult result
    ) {
        System.out.println(
                "\nEvent Verification Agent result"
        );

        System.out.println(
                "-------------------------------"
        );

        System.out.println(
                "Status: " + result.status()
        );

        System.out.printf(
                "Verification score: %.2f%n",
                result.verificationScore()
        );

        System.out.println(
                "Source domain: "
                        + result.normalizedSourceDomain()
        );

        System.out.println("Verification findings:");

        result.reasons().forEach(
                reason -> System.out.println(
                        "- " + reason
                )
        );

        if (result.status()
                == VerificationStatus.VERIFIED) {
            System.out.println(
                    "Gate decision: APPROVED for workload prediction"
            );
        } else {
            System.out.println(
                    "Gate decision: BLOCKED from automatic "
                            + "workload prediction"
            );
        }
    }

    private static boolean isQuitCommand(String input) {
        return QUIT_COMMAND.equalsIgnoreCase(input);
    }

    private static void stopAgent() {
        System.out.println("Event workflow stopped.");
    }

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

    private static void verifyApiKey() {
        String apiKey = System.getenv("GOOGLE_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_API_KEY is not configured."
            );
        }
    }
}