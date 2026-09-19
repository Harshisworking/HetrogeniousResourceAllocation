package com.harsh.cloudsim.agent.verification.evidence;

import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Command-line program for manually testing secure webpage evidence
 * retrieval before it is connected to the Verification Agent.
 */
public final class SourceEvidenceCliRunner {

    private static final String QUIT_COMMAND = "quit";
    private static final int PREVIEW_LENGTH = 800;

    private SourceEvidenceCliRunner() {
        // Application entry-point class; instances are unnecessary.
    }

    public static void main(String[] args) {
        SourceEvidenceFetcher fetcher =
                new HttpSourceEvidenceFetcher();

        System.out.println(
                "Secure Source Evidence Fetcher is ready."
        );

        System.out.println(
                "Enter an official HTTPS URL, or enter 'quit' to stop."
        );

        try (Scanner scanner = new Scanner(System.in, UTF_8)) {
            runInteractiveLoop(scanner, fetcher);
        }
    }

    private static void runInteractiveLoop(
            Scanner scanner,
            SourceEvidenceFetcher fetcher
    ) {
        while (true) {
            System.out.print("\nSource URL > ");

            String sourceReference =
                    scanner.nextLine().trim();

            if (QUIT_COMMAND.equalsIgnoreCase(
                    sourceReference
            )) {
                System.out.println(
                        "Evidence fetcher stopped."
                );

                return;
            }

            if (sourceReference.isBlank()) {
                System.out.println(
                        "Please enter a source URL."
                );

                continue;
            }

            System.out.println(
                    "\nRetrieving source evidence..."
            );

            SourceEvidence evidence =
                    fetcher.fetch(sourceReference);

            printEvidence(evidence);
        }
    }

    private static void printEvidence(
            SourceEvidence evidence
    ) {
        System.out.println(
                "\nSource evidence result"
        );

        System.out.println(
                "----------------------"
        );

        System.out.println(
                "Status: " + evidence.status()
        );

        System.out.println(
                "Requested source: "
                        + evidence.requestedSource()
        );

        System.out.println(
                "Final URL: " + evidence.finalUrl()
        );

        System.out.println(
                "HTTP status: "
                        + evidence.httpStatusCode()
        );

        System.out.println(
                "Retrieved at: "
                        + evidence.retrievedAt()
        );

        System.out.println(
                "Content type: "
                        + displayValue(
                        evidence.contentType()
                )
        );

        System.out.println(
                "Page title: "
                        + displayValue(
                        evidence.pageTitle()
                )
        );

        System.out.println(
                "Details: " + evidence.details()
        );

        if (!evidence.wasFetched()) {
            System.out.println(
                    "Evidence decision: NOT AVAILABLE"
            );

            return;
        }

        System.out.println(
                "Evidence decision: RETRIEVED"
        );

        System.out.println(
                "Visible-text length: "
                        + evidence.visibleText().length()
        );

        System.out.println(
                "\nVisible-text preview:"
        );

        System.out.println(
                createPreview(evidence.visibleText())
        );
    }

    private static String createPreview(String text) {
        if (text.length() <= PREVIEW_LENGTH) {
            return text;
        }

        return text.substring(0, PREVIEW_LENGTH)
                + "...";
    }

    private static String displayValue(String value) {
        if (value == null || value.isBlank()) {
            return "<not available>";
        }

        return value;
    }
}