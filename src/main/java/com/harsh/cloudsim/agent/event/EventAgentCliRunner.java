package com.harsh.cloudsim.agent.event;

import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.verification.EventVerificationAgent;
import com.harsh.cloudsim.agent.verification.claim.ClaimVerificationService;
import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerificationResult;
import com.harsh.cloudsim.agent.verification.evidence.HttpSourceEvidenceFetcher;
import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;
import com.harsh.cloudsim.agent.verification.evidence.SourceEvidenceFetcher;
import com.harsh.cloudsim.agent.verification.model.EventVerificationRequest;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;
import io.reactivex.rxjava3.core.Flowable;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Complete interactive event-intelligence and verification workflow.
 *
 * The workflow performs:
 *
 * 1. Gemini event-impact assessment.
 * 2. Deterministic preliminary validation.
 * 3. Secure source webpage retrieval.
 * 4. Gemini evidence-to-claim comparison.
 * 5. Final evidence-backed verification decision.
 */
public final class EventAgentCliRunner {

    private static final String USER_ID =
            "puranjay";

    private static final String QUIT_COMMAND =
            "quit";

    private EventAgentCliRunner() {
        // Application entry-point class; instances are unnecessary.
    }

    public static void main(String[] args) {
        verifyApiKey();

        RunConfig runConfig =
                RunConfig.builder().build();

        InMemoryRunner eventIntelligenceRunner =
                new InMemoryRunner(
                        EventIntelligenceAgent.ROOT_AGENT
                );

        Session eventSession =
                eventIntelligenceRunner
                        .sessionService()
                        .createSession(
                                eventIntelligenceRunner.appName(),
                                USER_ID
                        )
                        .blockingGet();

        EventVerificationAgent verificationAgent =
                new EventVerificationAgent();

        SourceEvidenceFetcher evidenceFetcher =
                new HttpSourceEvidenceFetcher();

        ClaimVerificationService claimVerificationService =
                new ClaimVerificationService();

        System.out.println(
                "Complete Event Intelligence and Verification "
                        + "workflow is ready."
        );

        System.out.println(
                "The workflow will analyse the event, retrieve the "
                        + "official source and verify the claim."
        );

        System.out.println(
                "Enter 'quit' at any prompt to stop."
        );

        try (Scanner scanner =
                     new Scanner(System.in, UTF_8)) {

            runInteractiveLoop(
                    scanner,
                    eventIntelligenceRunner,
                    eventSession,
                    runConfig,
                    verificationAgent,
                    evidenceFetcher,
                    claimVerificationService
            );
        }
    }

    private static void runInteractiveLoop(
            Scanner scanner,
            InMemoryRunner eventIntelligenceRunner,
            Session eventSession,
            RunConfig runConfig,
            EventVerificationAgent verificationAgent,
            SourceEvidenceFetcher evidenceFetcher,
            ClaimVerificationService claimVerificationService
    ) {
        while (true) {
            System.out.print(
                    "\nEvent description > "
            );

            String originalEventClaim =
                    scanner.nextLine().trim();

            if (isQuitCommand(originalEventClaim)) {
                stopWorkflow();
                return;
            }

            if (originalEventClaim.isBlank()) {
                System.out.println(
                        "Please enter an event description."
                );

                continue;
            }

            System.out.print(
                    "Official source URL > "
            );

            String sourceReference =
                    scanner.nextLine().trim();

            if (isQuitCommand(sourceReference)) {
                stopWorkflow();
                return;
            }

            if (sourceReference.isBlank()) {
                System.out.println(
                        "An official source URL is required "
                                + "for verification."
                );

                continue;
            }

            processEvent(
                    originalEventClaim,
                    sourceReference,
                    eventIntelligenceRunner,
                    eventSession,
                    runConfig,
                    verificationAgent,
                    evidenceFetcher,
                    claimVerificationService
            );
        }
    }

    private static void processEvent(
            String originalEventClaim,
            String sourceReference,
            InMemoryRunner eventIntelligenceRunner,
            Session eventSession,
            RunConfig runConfig,
            EventVerificationAgent verificationAgent,
            SourceEvidenceFetcher evidenceFetcher,
            ClaimVerificationService claimVerificationService
    ) {
        try {
            /*
             * Stage 1:
             * Ask the Event Intelligence Agent to convert the
             * unstructured event statement into a validated Java model.
             */
            System.out.println(
                    "\nStage 1: Analysing event impact..."
            );

            EventImpactAssessment assessment =
                    generateAssessment(
                            originalEventClaim,
                            eventIntelligenceRunner,
                            eventSession,
                            runConfig
                    );

            printAssessment(assessment);

            EventVerificationRequest verificationRequest =
                    new EventVerificationRequest(
                            assessment,
                            sourceReference
                    );

            /*
             * Stage 2:
             * Run inexpensive deterministic checks before making
             * a network request or another Gemini request.
             */
            System.out.println(
                    "\nStage 2: Running preliminary safety checks..."
            );

            EventVerificationResult preliminaryResult =
                    verificationAgent.verify(
                            verificationRequest
                    );

            if (preliminaryResult.status()
                    == VerificationStatus.REJECTED) {

                System.out.println(
                        "Preliminary safety checks rejected the event."
                );

                printVerificationResult(
                        preliminaryResult
                );

                return;
            }

            System.out.println(
                    "Preliminary checks passed. "
                            + "External evidence is still required."
            );

            /*
             * Stage 3:
             * Retrieve visible text from the official webpage using
             * the secure evidence fetcher.
             */
            System.out.println(
                    "\nStage 3: Retrieving official source evidence..."
            );

            SourceEvidence evidence =
                    evidenceFetcher.fetch(
                            sourceReference
                    );

            printEvidenceSummary(evidence);

            EventVerificationResult evidenceResult =
                    verificationAgent.verify(
                            verificationRequest,
                            evidence
                    );

            /*
             * A failed, blocked or missing page must stop the pipeline.
             * Gemini is not called when no usable evidence exists.
             */
            if (evidenceResult.status()
                    == VerificationStatus.REJECTED) {

                printVerificationResult(
                        evidenceResult
                );

                return;
            }

            /*
             * Stage 4:
             * Ask the Claim Verification Agent to compare the original
             * human-written claim against the actual webpage content.
             *
             * It is important to pass originalEventClaim here instead
             * of the Event Intelligence Agent's reasoning. This prevents
             * one LLM's generated interpretation from becoming the fact
             * that another LLM verifies.
             */
            System.out.println(
                    "\nStage 4: Comparing the original claim "
                            + "with webpage evidence..."
            );

            ClaimVerificationResult claimResult =
                    claimVerificationService.verifyClaim(
                            originalEventClaim,
                            evidence
                    );

            printClaimResult(
                    claimResult
            );

            /*
             * Stage 5:
             * Combine deterministic checks, source retrieval and
             * semantic claim matching into the final decision.
             */
            System.out.println(
                    "\nStage 5: Producing final verification decision..."
            );

            EventVerificationResult finalResult =
                    verificationAgent.verify(
                            verificationRequest,
                            evidence,
                            claimResult
                    );

            printVerificationResult(
                    finalResult
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
        Content userMessage =
                Content.fromParts(
                        Part.fromText(userInput)
                );

        Flowable<Event> events =
                runner.runAsync(
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

        String rawResponse =
                finalResponse.get();

        if (rawResponse == null
                || rawResponse.isBlank()) {
            throw new EventAssessmentParsingException(
                    "Gemini did not return a final response."
            );
        }

        return EventAssessmentParser.parse(
                rawResponse
        );
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
                "Event name: "
                        + assessment.eventName()
        );

        System.out.println(
                "Event type: "
                        + assessment.eventType()
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
                "Event-agent confidence: %.2f%n",
                assessment.confidence()
        );

        System.out.println(
                "Event-agent reasoning: "
                        + assessment.reasoning()
        );
    }

    private static void printEvidenceSummary(
            SourceEvidence evidence
    ) {
        System.out.println(
                "\nSource evidence result"
        );

        System.out.println(
                "----------------------"
        );

        System.out.println(
                "Evidence status: "
                        + evidence.status()
        );

        System.out.println(
                "HTTP status: "
                        + evidence.httpStatusCode()
        );

        System.out.println(
                "Final URL: "
                        + evidence.finalUrl()
        );

        System.out.println(
                "Page title: "
                        + displayOrUnknown(
                        evidence.pageTitle()
                )
        );

        if (evidence.wasFetched()) {
            System.out.println(
                    "Visible-text length: "
                            + evidence.visibleText().length()
            );

            System.out.println(
                    "Retrieval time: "
                            + evidence.retrievedAt()
            );
        } else {
            System.out.println(
                    "Evidence details: "
                            + evidence.details()
            );
        }
    }

    private static void printClaimResult(
            ClaimVerificationResult result
    ) {
        System.out.println(
                "\nGemini semantic claim-verification result"
        );

        System.out.println(
                "-----------------------------------------"
        );

        System.out.println(
                "Verdict: "
                        + result.verdict()
        );

        System.out.printf(
                "Confidence: %.2f%n",
                result.confidence()
        );

        System.out.println(
                "Supported facts:"
        );

        if (result.supportedFacts().isEmpty()) {
            System.out.println("- None");
        } else {
            result.supportedFacts().forEach(
                    fact -> System.out.println(
                            "- " + fact
                    )
            );
        }

        System.out.println(
                "Missing or contradicted facts:"
        );

        if (result
                .missingOrContradictedFacts()
                .isEmpty()) {
            System.out.println("- None");
        } else {
            result.missingOrContradictedFacts()
                    .forEach(
                            fact -> System.out.println(
                                    "- " + fact
                            )
                    );
        }

        System.out.println(
                "Evidence excerpts:"
        );

        if (result.evidenceExcerpts().isEmpty()) {
            System.out.println("- None");
        } else {
            result.evidenceExcerpts().forEach(
                    excerpt -> System.out.println(
                            "- \"" + excerpt + "\""
                    )
            );
        }

        System.out.println(
                "Semantic reasoning: "
                        + result.reasoning()
        );
    }

    private static void printVerificationResult(
            EventVerificationResult result
    ) {
        System.out.println(
                "\nFinal Event Verification Agent result"
        );

        System.out.println(
                "-------------------------------------"
        );

        System.out.println(
                "Status: "
                        + result.status()
        );

        System.out.printf(
                "Verification score: %.2f%n",
                result.verificationScore()
        );

        System.out.println(
                "Source domain: "
                        + result.normalizedSourceDomain()
        );

        System.out.println(
                "Verification findings:"
        );

        result.reasons().forEach(
                reason -> System.out.println(
                        "- " + reason
                )
        );

        switch (result.status()) {
            case VERIFIED ->
                    System.out.println(
                            "Gate decision: APPROVED for "
                                    + "workload prediction"
                    );

            case MANUAL_REVIEW ->
                    System.out.println(
                            "Gate decision: MANUAL REVIEW required; "
                                    + "blocked from automatic "
                                    + "workload prediction"
                    );

            case REJECTED ->
                    System.out.println(
                            "Gate decision: REJECTED and blocked "
                                    + "from workload prediction"
                    );
        }
    }

    private static boolean isQuitCommand(
            String input
    ) {
        return QUIT_COMMAND.equalsIgnoreCase(
                input
        );
    }

    private static void stopWorkflow() {
        System.out.println(
                "Event workflow stopped."
        );
    }

    private static String displayOrUnknown(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return "unknown";
        }

        return value;
    }

    private static String readableMessage(
            Throwable throwable
    ) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message =
                current.getMessage();

        if (message == null
                || message.isBlank()) {
            return current.getClass()
                    .getSimpleName();
        }

        return message;
    }

    /**
     * Fails immediately when the Gemini API key is unavailable.
     */
    private static void verifyApiKey() {
        String apiKey =
                System.getenv("GOOGLE_API_KEY");

        if (apiKey == null
                || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_API_KEY is not configured."
            );
        }
    }
}