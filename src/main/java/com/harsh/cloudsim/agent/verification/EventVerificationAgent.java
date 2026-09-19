package com.harsh.cloudsim.agent.verification;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.verification.model.EventVerificationRequest;
import com.harsh.cloudsim.agent.verification.model.EventVerificationResult;
import com.harsh.cloudsim.agent.verification.model.VerificationStatus;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Performs preliminary deterministic checks on an event assessment.
 *
 * This agent currently checks source-domain trust and assessment
 * consistency. It cannot return VERIFIED until actual webpage evidence
 * retrieval and claim matching are implemented.
 */
public final class EventVerificationAgent {

    private static final double VERIFIED_CONFIDENCE_THRESHOLD =
            0.75;

    private static final double REJECTION_CONFIDENCE_THRESHOLD =
            0.50;

    private static final int MINIMUM_REASONING_LENGTH =
            20;

    private static final Set<String> DEFAULT_TRUSTED_DOMAINS =
            Set.of(
                    "ipu.ac.in",
                    "nta.ac.in",
                    "cbse.gov.in",
                    "india.gov.in",
                    "irctc.co.in"
            );

    private final Set<String> trustedDomains;

    /**
     * Creates an agent using the project's default trusted sources.
     */
    public EventVerificationAgent() {
        this(DEFAULT_TRUSTED_DOMAINS);
    }

    /**
     * Creates an agent with an explicitly configured trusted-domain set.
     */
    public EventVerificationAgent(
            Set<String> trustedDomains
    ) {
        if (trustedDomains == null
                || trustedDomains.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one trusted domain is required."
            );
        }

        HashSet<String> normalizedDomains =
                new HashSet<>();

        for (String domain : trustedDomains) {
            String normalized = normalizeDomain(domain)
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "Invalid trusted domain: " + domain
                            )
                    );

            normalizedDomains.add(normalized);
        }

        this.trustedDomains =
                Set.copyOf(normalizedDomains);
    }

    /**
     * Performs preliminary verification on one event assessment.
     */
    public EventVerificationResult verify(
            EventVerificationRequest request
    ) {
        Objects.requireNonNull(
                request,
                "Verification request must not be null."
        );

        EventImpactAssessment assessment =
                request.assessment();

        Optional<String> sourceDomain =
                normalizeDomain(
                        request.sourceReference()
                );

        Optional<String> serviceDomain =
                normalizeDomain(
                        assessment.affectedService()
                );

        ArrayList<String> reasons =
                new ArrayList<>();

        double score = 0.0;

        boolean sourceIsValid =
                sourceDomain.isPresent();

        boolean serviceIsValid =
                serviceDomain.isPresent();

        boolean sourceIsTrusted =
                sourceDomain
                        .map(this::isTrustedDomain)
                        .orElse(false);

        boolean audienceIsSpecified =
                assessment.expectedUsers() > 0;

        boolean reasoningIsDetailed =
                assessment.reasoning().length()
                        >= MINIMUM_REASONING_LENGTH;

        boolean confidenceSupportsVerification =
                assessment.confidence()
                        >= VERIFIED_CONFIDENCE_THRESHOLD;

        boolean confidenceRequiresRejection =
                assessment.confidence()
                        < REJECTION_CONFIDENCE_THRESHOLD;

        if (!sourceIsValid) {
            reasons.add(
                    "Source reference is malformed or has no valid domain."
            );
        } else if (sourceIsTrusted) {
            score += 0.40;

            reasons.add(
                    "Source domain is included in the trusted-domain set."
            );
        } else {
            reasons.add(
                    "Source domain is not included in the "
                            + "trusted-domain set."
            );
        }

        if (confidenceSupportsVerification) {
            score += 0.25;

            reasons.add(
                    "Assessment confidence satisfies the preliminary "
                            + "verification threshold."
            );
        } else if (!confidenceRequiresRejection) {
            score += 0.10;

            reasons.add(
                    "Assessment confidence requires manual review."
            );
        } else {
            reasons.add(
                    "Assessment confidence is below the minimum "
                            + "acceptable threshold."
            );
        }

        if (serviceIsValid) {
            score += 0.10;

            reasons.add(
                    "Affected service contains a valid domain."
            );
        } else {
            reasons.add(
                    "Affected service does not contain a valid domain."
            );
        }

        if (audienceIsSpecified) {
            score += 0.10;

            reasons.add(
                    "Expected audience size is specified."
            );
        } else {
            reasons.add(
                    "Expected audience size is unknown or zero."
            );
        }

        if (reasoningIsDetailed) {
            score += 0.10;

            reasons.add(
                    "Assessment includes sufficiently detailed reasoning."
            );
        } else {
            reasons.add(
                    "Assessment reasoning is too short for "
                            + "automatic verification."
            );
        }

        if (sourceDomain.isPresent()
                && serviceDomain.isPresent()
                && domainsAreRelated(
                sourceDomain.get(),
                serviceDomain.get()
        )) {
            score += 0.05;

            reasons.add(
                    "Source and affected-service domains are related."
            );
        } else {
            reasons.add(
                    "Source and affected-service domains are different "
                            + "or unavailable."
            );
        }

        VerificationStatus status =
                determineStatus(
                        sourceIsValid,
                        serviceIsValid,
                        confidenceRequiresRejection
                );

        reasons.add(
                "Final preliminary status: " + status + "."
        );

        if (status == VerificationStatus.MANUAL_REVIEW) {
            reasons.add(
                    "Actual source-page evidence has not yet been "
                            + "retrieved and matched against the claim."
            );
        }

        return new EventVerificationResult(
                status,
                Math.min(score, 1.0),
                sourceDomain.orElse("unknown"),
                reasons
        );
    }

    /**
     * A trusted domain is not factual proof that an event exists.
     *
     * Until webpage retrieval and content matching are implemented,
     * a valid assessment can reach only MANUAL_REVIEW.
     */
    private VerificationStatus determineStatus(
            boolean sourceIsValid,
            boolean serviceIsValid,
            boolean confidenceRequiresRejection
    ) {
        if (!sourceIsValid
                || !serviceIsValid
                || confidenceRequiresRejection) {
            return VerificationStatus.REJECTED;
        }

        return VerificationStatus.MANUAL_REVIEW;
    }

    /**
     * Uses exact-domain and real-subdomain matching.
     *
     * admissions.ipu.ac.in is trusted for ipu.ac.in.
     * ipu.ac.in.attacker.example is not trusted.
     */
    private boolean isTrustedDomain(String sourceDomain) {
        return trustedDomains.stream().anyMatch(
                trustedDomain ->
                        sourceDomain.equals(trustedDomain)
                                || sourceDomain.endsWith(
                                "." + trustedDomain
                        )
        );
    }

    private static boolean domainsAreRelated(
            String firstDomain,
            String secondDomain
    ) {
        return firstDomain.equals(secondDomain)
                || firstDomain.endsWith(
                "." + secondDomain
        )
                || secondDomain.endsWith(
                "." + firstDomain
        );
    }

    /**
     * Extracts and normalizes a domain from a complete URL or a
     * plain domain name.
     */
    private static Optional<String> normalizeDomain(
            String sourceReference
    ) {
        if (sourceReference == null
                || sourceReference.isBlank()) {
            return Optional.empty();
        }

        String value = sourceReference.trim();

        if ("UNSPECIFIED".equalsIgnoreCase(value)
                || "UNKNOWN".equalsIgnoreCase(value)) {
            return Optional.empty();
        }

        String candidate = value.contains("://")
                ? value
                : "https://" + value;

        try {
            URI uri = new URI(candidate);

            String scheme = uri.getScheme();

            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme))) {
                return Optional.empty();
            }

            /*
             * Reject URLs such as:
             * https://ipu.ac.in@attacker.example
             */
            if (uri.getUserInfo() != null) {
                return Optional.empty();
            }

            String host = uri.getHost();

            if (host == null || host.isBlank()) {
                return Optional.empty();
            }

            String normalized = IDN.toASCII(host)
                    .toLowerCase(Locale.ROOT);

            while (normalized.endsWith(".")) {
                normalized = normalized.substring(
                        0,
                        normalized.length() - 1
                );
            }

            if (normalized.startsWith("www.")) {
                normalized = normalized.substring(4);
            }

            if (normalized.isBlank()
                    || !normalized.contains(".")) {
                return Optional.empty();
            }

            return Optional.of(normalized);
        } catch (URISyntaxException
                 | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}