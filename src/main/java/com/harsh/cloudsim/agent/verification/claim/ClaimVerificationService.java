package com.harsh.cloudsim.agent.verification.claim;

import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerificationResult;
import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;
import io.reactivex.rxjava3.core.Flowable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the Gemini Claim Verification Agent and converts its JSON
 * response into a validated ClaimVerificationResult.
 */
public final class ClaimVerificationService {

    private static final String USER_ID =
            "claim-verification-user";

    /**
     * Limits prompt size and prevents an unexpectedly large webpage
     * from being sent to the model.
     */
    private static final int MAX_EVIDENCE_CHARACTERS =
            12_000;

    private final InMemoryRunner runner;
    private final RunConfig runConfig;
    private final Gson gson;

    public ClaimVerificationService() {
        verifyApiKey();

        this.runner = new InMemoryRunner(
                ClaimVerificationAgent.ROOT_AGENT
        );

        this.runConfig =
                RunConfig.builder().build();

        this.gson = new Gson();
    }

    /**
     * Compares the original claim with successfully retrieved evidence.
     */
    public ClaimVerificationResult verifyClaim(
            String originalClaim,
            SourceEvidence evidence
    ) {
        if (originalClaim == null
                || originalClaim.isBlank()) {
            throw new IllegalArgumentException(
                    "Original event claim must not be blank."
            );
        }

        Objects.requireNonNull(
                evidence,
                "Source evidence must not be null."
        );

        if (!evidence.wasFetched()) {
            throw new IllegalArgumentException(
                    "Claim verification requires successfully "
                            + "retrieved source evidence."
            );
        }

        Session session = runner
                .sessionService()
                .createSession(
                        runner.appName(),
                        USER_ID
                )
                .blockingGet();

        String verificationInput =
                createVerificationInput(
                        originalClaim,
                        evidence
                );

        Content userMessage = Content.fromParts(
                Part.fromText(verificationInput)
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

        String rawResponse =
                finalResponse.get();

        if (rawResponse == null
                || rawResponse.isBlank()) {
            throw new ClaimVerificationParsingException(
                    "Gemini did not return a final "
                            + "claim-verification response."
            );
        }

        return ClaimVerificationResultParser.parse(
                rawResponse
        );
    }

    /**
     * Serializes all untrusted input as JSON instead of directly
     * interpolating it into the instruction text.
     */
    private String createVerificationInput(
            String originalClaim,
            SourceEvidence evidence
    ) {
        JsonObject input = new JsonObject();

        input.addProperty(
                "task",
                "Compare the original event claim only with "
                        + "the supplied source evidence."
        );

        input.addProperty(
                "originalClaim",
                originalClaim.trim()
        );

        input.addProperty(
                "sourceUrl",
                evidence.finalUrl()
        );

        input.addProperty(
                "pageTitle",
                evidence.pageTitle()
        );

        input.addProperty(
                "retrievedAt",
                evidence.retrievedAt().toString()
        );

        input.addProperty(
                "sourceEvidence",
                limitEvidenceText(
                        evidence.visibleText()
                )
        );

        return gson.toJson(input);
    }

    private static String limitEvidenceText(
            String evidenceText
    ) {
        if (evidenceText.length()
                <= MAX_EVIDENCE_CHARACTERS) {
            return evidenceText;
        }

        return evidenceText.substring(
                0,
                MAX_EVIDENCE_CHARACTERS
        );
    }

    private static void verifyApiKey() {
        String apiKey =
                System.getenv("GOOGLE_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_API_KEY is not configured."
            );
        }
    }
}