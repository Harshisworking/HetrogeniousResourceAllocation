package com.harsh.cloudsim.agent.verification.claim;

import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerificationResult;
import com.harsh.cloudsim.agent.verification.evidence.HttpSourceEvidenceFetcher;
import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;
import com.harsh.cloudsim.agent.verification.evidence.SourceEvidenceFetcher;

import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Command-line program that retrieves webpage evidence and asks
 * Gemini whether that evidence supports an event claim.
 */
public final class ClaimVerificationCliRunner {

    private static final String QUIT_COMMAND =
            "quit";

    private ClaimVerificationCliRunner() {
        // Application entry-point class; instances are unnecessary.
    }

    public static void main(String[] args) {
        SourceEvidenceFetcher evidenceFetcher =
                new HttpSourceEvidenceFetcher();

        ClaimVerificationService verificationService =
                new ClaimVerificationService();

        System.out.println(
                "Evidence-based Claim Verification Agent is ready."
        );

        System.out.println(
                "Enter 'quit' at any prompt to stop."
        );

        try (Scanner scanner =
                     new Scanner(System.in, UTF_8)) {

            while (true) {
                System.out.print("\nEvent claim > ");

                String originalClaim =
                        scanner.nextLine().trim();

                if (isQuitCommand(originalClaim)) {
                    stop();
                    return;
                }

                if (originalClaim.isBlank()) {
                    System.out.println(
                            "Please enter an event claim."
                    );

                    continue;
                }

                System.out.print(
                        "Official source URL > "
                );

                String sourceReference =
                        scanner.nextLine().trim();

                if (isQuitCommand(sourceReference)) {
                    stop();
                    return;
                }

                if (sourceReference.isBlank()) {
                    System.out.println(
                            "Please enter an official source URL."
                    );

                    continue;
                }

                verifyClaim(
                        originalClaim,
                        sourceReference,
                        evidenceFetcher,
                        verificationService
                );
            }
        }
    }

    private static void verifyClaim(
            String originalClaim,
            String sourceReference,
            SourceEvidenceFetcher evidenceFetcher,
            ClaimVerificationService verificationService
    ) {
        System.out.println(
                "\nRetrieving source evidence..."
        );

        SourceEvidence evidence =
                evidenceFetcher.fetch(
                        sourceReference
                );

        printEvidenceSummary(evidence);

        if (!evidence.wasFetched()) {
            System.out.println(
                    "Claim verdict: NOT_SUPPORTED"
            );

            System.out.println(
                    "Reason: Source evidence could not be retrieved."
            );

            System.out.println(
                    "Gate decision: BLOCKED"
            );

            return;
        }

        try {
            System.out.println(
                    "\nComparing claim with source evidence..."
            );

            ClaimVerificationResult result =
                    verificationService.verifyClaim(
                            originalClaim,
                            evidence
                    );

            printClaimResult(result);
        } catch (RuntimeException exception) {
            System.err.println(
                    "Claim verification failed: "
                            + readableMessage(exception)
            );

            System.err.println(
                    "Gate decision: BLOCKED"
            );
        }
    }

    private static void printEvidenceSummary(
            SourceEvidence evidence
    ) {
        System.out.println(
                "Evidence status: " + evidence.status()
        );

        System.out.println(
                "HTTP status: " + evidence.httpStatusCode()
        );

        System.out.println(
                "Final URL: " + evidence.finalUrl()
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
                "\nClaim verification result"
        );

        System.out.println(
                "-------------------------"
        );

        System.out.println(
                "Verdict: " + result.verdict()
        );

        System.out.printf(
                "Confidence: %.2f%n",
                result.confidence()
        );

        System.out.println("Supported facts:");

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

        result.missingOrContradictedFacts()
                .forEach(
                        fact -> System.out.println(
                                "- " + fact
                        )
                );

        System.out.println("Evidence excerpts:");

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
                "Reasoning: " + result.reasoning()
        );

        switch (result.verdict()) {
            case SUPPORTED -> System.out.println(
                    "Evidence gate: PASSED"
            );

            case PARTIALLY_SUPPORTED ->
                    System.out.println(
                            "Evidence gate: MANUAL REVIEW"
                    );

            case NOT_SUPPORTED ->
                    System.out.println(
                            "Evidence gate: BLOCKED"
                    );
        }
    }

    private static boolean isQuitCommand(
            String input
    ) {
        return QUIT_COMMAND.equalsIgnoreCase(input);
    }

    private static void stop() {
        System.out.println(
                "Claim Verification Agent stopped."
        );
    }

    private static String displayOrUnknown(
            String value
    ) {
        if (value == null || value.isBlank()) {
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

        String message = current.getMessage();

        if (message == null || message.isBlank()) {
            return current.getClass()
                    .getSimpleName();
        }

        return message;
    }
}