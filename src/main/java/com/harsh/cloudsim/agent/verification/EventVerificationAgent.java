package com.harsh.cloudsim.agent.verification;

import com.harsh.cloudsim.agent.event.model.EventImpactAssessment;
import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerdict;
import com.harsh.cloudsim.agent.verification.claim.model.ClaimVerificationResult;
import com.harsh.cloudsim.agent.verification.evidence.SourceEvidence;
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
 * Performs evidence-backed verification of an event assessment.
 *
 * Verification happens in three stages:
 *
 * 1. Preliminary deterministic validation.
 * 2. Secure source-evidence validation.
 * 3. Semantic claim-verification validation.
 *
 * A trusted domain alone is never treated as proof that an event
 * actually exists.
 */
public final class EventVerificationAgent {

    /**
     * Minimum Event Intelligence Agent confidence required for
     * automatic verification.
     */
    private static final double VERIFIED_CONFIDENCE_THRESHOLD =
            0.75;

    /**
     * Assessments below this confidence are rejected.
     */
    private static final double REJECTION_CONFIDENCE_THRESHOLD =
            0.50;

    /**
     * Minimum semantic claim-verification confidence required for
     * automatic verification.
     */
    private static final double CLAIM_CONFIDENCE_THRESHOLD =
            0.80;

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
     * Creates an agent using the default trusted-domain set.
     */
    public EventVerificationAgent() {
        this(DEFAULT_TRUSTED_DOMAINS);
    }

    /**
     * Creates an agent using an explicitly configured trusted-domain
     * set.
     *
     * @param trustedDomains official domains that the agent may trust
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
            String normalized =
                    normalizeDomain(domain)
                            .orElseThrow(
                                    () -> new IllegalArgumentException(
                                            "Invalid trusted domain: "
                                                    + domain
                                    )
                            );

            normalizedDomains.add(normalized);
        }

        this.trustedDomains =
                Set.copyOf(normalizedDomains);
    }

    /**
     * Stage 1: performs preliminary deterministic checks.
     *
     * This method cannot return VERIFIED because webpage evidence
     * and semantic claim verification have not yet been supplied.
     *
     * @param request assessment and claimed source
     * @return preliminary result
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

        /*
         * affectedService may be a domain such as ipu.ac.in or a
         * human-readable service name such as "GGSIPU Admission
         * Portal". A descriptive service name is allowed.
         */
        Optional<String> serviceDomain =
                normalizeDomain(
                        assessment.affectedService()
                );

        ArrayList<String> reasons =
                new ArrayList<>();

        double score = 0.0;

        boolean sourceIsValid =
                sourceDomain.isPresent();

        boolean serviceContainsDomain =
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
                    "Source reference is malformed or has no "
                            + "valid domain."
            );
        } else if (sourceIsTrusted) {
            score += 0.40;

            reasons.add(
                    "Source domain is included in the "
                            + "trusted-domain set."
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
                    "Assessment confidence satisfies the "
                            + "preliminary verification threshold."
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

        if (serviceContainsDomain) {
            score += 0.10;

            reasons.add(
                    "Affected service contains a valid domain."
            );
        } else {
            reasons.add(
                    "Affected service is a descriptive service name "
                            + "or does not contain a domain."
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
                    "Assessment includes sufficiently detailed "
                            + "reasoning."
            );
        } else {
            reasons.add(
                    "Assessment reasoning is too short for "
                            + "automatic verification."
            );
        }

        boolean domainsRelated =
                sourceDomain.isPresent()
                        && serviceDomain.isPresent()
                        && domainsAreRelated(
                        sourceDomain.get(),
                        serviceDomain.get()
                );

        if (domainsRelated) {
            score += 0.05;

            reasons.add(
                    "Source and affected-service domains are related."
            );
        } else if (serviceContainsDomain) {
            reasons.add(
                    "Source and affected-service domains are "
                            + "different."
            );
        } else {
            reasons.add(
                    "Domain comparison was not performed because "
                            + "the affected service is represented "
                            + "by a descriptive name."
            );
        }

        VerificationStatus status;

        /*
         * A human-readable affectedService does not cause rejection.
         *
         * Hard rejection at this stage happens only when the source
         * is malformed or the assessment confidence is extremely low.
         */
        if (!sourceIsValid
                || confidenceRequiresRejection) {
            status = VerificationStatus.REJECTED;
        } else {
            status = VerificationStatus.MANUAL_REVIEW;
        }

        reasons.add(
                "Final preliminary status: "
                        + status
                        + "."
        );

        if (status == VerificationStatus.MANUAL_REVIEW) {
            reasons.add(
                    "Preliminary checks cannot prove the event. "
                            + "Webpage evidence and semantic claim "
                            + "matching are still required."
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
     * Stage 2: adds the webpage-evidence retrieval result.
     *
     * A failed evidence retrieval causes rejection. Successfully
     * fetched evidence remains under manual review until semantic
     * claim verification is completed.
     *
     * @param request  event verification request
     * @param evidence webpage retrieval result
     * @return evidence-aware intermediate result
     */
    public EventVerificationResult verify(
            EventVerificationRequest request,
            SourceEvidence evidence
    ) {
        Objects.requireNonNull(
                evidence,
                "Source evidence must not be null."
        );

        EventVerificationResult preliminary =
                verify(request);

        ArrayList<String> reasons =
                new ArrayList<>(
                        preliminary.reasons()
                );

        if (preliminary.status()
                == VerificationStatus.REJECTED) {
            reasons.add(
                    "Evidence processing cannot override a rejected "
                            + "preliminary assessment."
            );

            reasons.add(
                    "Final evidence status: REJECTED."
            );

            return new EventVerificationResult(
                    VerificationStatus.REJECTED,
                    rejectionScore(
                            preliminary.verificationScore(),
                            0.0
                    ),
                    preliminary.normalizedSourceDomain(),
                    reasons
            );
        }

        if (!evidence.wasFetched()) {
            reasons.add(
                    "Source evidence was not retrieved successfully."
            );

            reasons.add(
                    "Evidence-fetch status: "
                            + evidence.status()
                            + "."
            );

            reasons.add(
                    "Evidence-fetch details: "
                            + evidence.details()
            );

            reasons.add(
                    "The event is rejected because its factual claim "
                            + "cannot be checked against source content."
            );

            reasons.add(
                    "Final evidence status: REJECTED."
            );

            return new EventVerificationResult(
                    VerificationStatus.REJECTED,
                    rejectionScore(
                            preliminary.verificationScore(),
                            0.0
                    ),
                    preliminary.normalizedSourceDomain(),
                    reasons
            );
        }

        Optional<String> requestedDomain =
                normalizeDomain(
                        request.sourceReference()
                );

        Optional<String> finalEvidenceDomain =
                normalizeDomain(
                        evidence.finalUrl()
                );

        boolean finalDomainIsValid =
                finalEvidenceDomain.isPresent();

        boolean finalDomainIsTrusted =
                finalEvidenceDomain
                        .map(this::isTrustedDomain)
                        .orElse(false);

        boolean redirectStayedRelated =
                requestedDomain.isPresent()
                        && finalEvidenceDomain.isPresent()
                        && domainsAreRelated(
                        requestedDomain.get(),
                        finalEvidenceDomain.get()
                );

        /*
         * The evidence fetcher already performs redirect checks.
         * This independent validation creates a second safety layer.
         */
        if (!finalDomainIsValid
                || !finalDomainIsTrusted
                || !redirectStayedRelated) {
            reasons.add(
                    "Fetched evidence ended at an invalid, untrusted "
                            + "or unrelated domain."
            );

            reasons.add(
                    "Final evidence URL: "
                            + evidence.finalUrl()
            );

            reasons.add(
                    "Final evidence status: REJECTED."
            );

            return new EventVerificationResult(
                    VerificationStatus.REJECTED,
                    rejectionScore(
                            preliminary.verificationScore(),
                            0.0
                    ),
                    preliminary.normalizedSourceDomain(),
                    reasons
            );
        }

        reasons.add(
                "Source webpage was retrieved successfully."
        );

        reasons.add(
                "Evidence HTTP status: "
                        + evidence.httpStatusCode()
                        + "."
        );

        reasons.add(
                "Evidence final URL remained on a trusted "
                        + "and related domain."
        );

        reasons.add(
                "Source content has not yet been semantically matched "
                        + "against the event claim."
        );

        reasons.add(
                "Final evidence status: MANUAL_REVIEW."
        );

        return new EventVerificationResult(
                VerificationStatus.MANUAL_REVIEW,
                manualReviewScore(
                        preliminary.verificationScore(),
                        1.0
                ),
                preliminary.normalizedSourceDomain(),
                reasons
        );
    }

    /**
     * Stage 3: produces the final evidence-backed decision.
     *
     * VERIFIED requires:
     *
     * - a valid and trusted source;
     * - successfully fetched webpage evidence;
     * - a SUPPORTED semantic claim verdict;
     * - sufficient semantic confidence;
     * - sufficient event-assessment confidence;
     * - a specified expected audience;
     * - sufficiently detailed assessment reasoning.
     *
     * @param request     event verification request
     * @param evidence    retrieved webpage evidence
     * @param claimResult semantic claim-verification result
     * @return final verification result
     */
    public EventVerificationResult verify(
            EventVerificationRequest request,
            SourceEvidence evidence,
            ClaimVerificationResult claimResult
    ) {
        Objects.requireNonNull(
                claimResult,
                "Claim-verification result must not be null."
        );

        EventVerificationResult evidenceResult =
                verify(
                        request,
                        evidence
                );

        ArrayList<String> reasons =
                new ArrayList<>(
                        evidenceResult.reasons()
                );

        if (evidenceResult.status()
                == VerificationStatus.REJECTED) {
            reasons.add(
                    "Semantic claim matching cannot override the "
                            + "rejected evidence result."
            );

            reasons.add(
                    "Final verification status: REJECTED."
            );

            return new EventVerificationResult(
                    VerificationStatus.REJECTED,
                    rejectionScore(
                            evidenceResult.verificationScore(),
                            claimResult.confidence()
                    ),
                    evidenceResult.normalizedSourceDomain(),
                    reasons
            );
        }

        reasons.add(
                "Semantic claim verdict: "
                        + claimResult.verdict()
                        + "."
        );

        reasons.add(
                String.format(
                        Locale.ROOT,
                        "Semantic claim confidence: %.2f.",
                        claimResult.confidence()
                )
        );

        reasons.add(
                "Semantic reasoning: "
                        + claimResult.reasoning()
        );

        if (!claimResult.supportedFacts().isEmpty()) {
            reasons.add(
                    "Supported fact count: "
                            + claimResult
                            .supportedFacts()
                            .size()
                            + "."
            );
        }

        if (!claimResult
                .missingOrContradictedFacts()
                .isEmpty()) {
            reasons.add(
                    "Missing or contradicted fact count: "
                            + claimResult
                            .missingOrContradictedFacts()
                            .size()
                            + "."
            );
        }

        if (claimResult.verdict()
                == ClaimVerdict.NOT_SUPPORTED) {
            reasons.add(
                    "The retrieved webpage does not support the "
                            + "submitted event claim."
            );

            reasons.add(
                    "Final verification status: REJECTED."
            );

            return new EventVerificationResult(
                    VerificationStatus.REJECTED,
                    rejectionScore(
                            evidenceResult.verificationScore(),
                            claimResult.confidence()
                    ),
                    evidenceResult.normalizedSourceDomain(),
                    reasons
            );
        }

        if (claimResult.verdict()
                == ClaimVerdict.PARTIALLY_SUPPORTED) {
            reasons.add(
                    "The webpage supports only part of the event claim."
            );

            reasons.add(
                    "Human review is required for missing or "
                            + "contradicted facts."
            );

            reasons.add(
                    "Final verification status: MANUAL_REVIEW."
            );

            return new EventVerificationResult(
                    VerificationStatus.MANUAL_REVIEW,
                    manualReviewScore(
                            evidenceResult.verificationScore(),
                            claimResult.confidence()
                    ),
                    evidenceResult.normalizedSourceDomain(),
                    reasons
            );
        }

        if (claimResult.confidence()
                < CLAIM_CONFIDENCE_THRESHOLD) {
            reasons.add(
                    "The claim is marked as supported, but semantic "
                            + "verification confidence is below "
                            + CLAIM_CONFIDENCE_THRESHOLD
                            + "."
            );

            reasons.add(
                    "Final verification status: MANUAL_REVIEW."
            );

            return new EventVerificationResult(
                    VerificationStatus.MANUAL_REVIEW,
                    manualReviewScore(
                            evidenceResult.verificationScore(),
                            claimResult.confidence()
                    ),
                    evidenceResult.normalizedSourceDomain(),
                    reasons
            );
        }

        EventImpactAssessment assessment =
                request.assessment();

        Optional<String> sourceDomain =
                normalizeDomain(
                        request.sourceReference()
                );

        boolean sourceIsTrusted =
                sourceDomain
                        .map(this::isTrustedDomain)
                        .orElse(false);

        /*
         * Affected service does not have to be a domain. It can be a
         * descriptive service name such as "GGSIPU Admission Portal".
         *
         * Factual validation is performed using the source evidence
         * and semantic claim-verification result.
         */
        boolean automaticRequirementsSatisfied =
                sourceIsTrusted
                        && assessment.confidence()
                        >= VERIFIED_CONFIDENCE_THRESHOLD
                        && assessment.expectedUsers() > 0
                        && assessment.reasoning().length()
                        >= MINIMUM_REASONING_LENGTH;

        if (!automaticRequirementsSatisfied) {
            reasons.add(
                    "The semantic claim is supported, but one or more "
                            + "deterministic automatic-verification "
                            + "requirements are not satisfied."
            );

            reasons.add(
                    "Final verification status: MANUAL_REVIEW."
            );

            return new EventVerificationResult(
                    VerificationStatus.MANUAL_REVIEW,
                    manualReviewScore(
                            evidenceResult.verificationScore(),
                            claimResult.confidence()
                    ),
                    evidenceResult.normalizedSourceDomain(),
                    reasons
            );
        }

        reasons.add(
                "The official webpage evidence supports the submitted "
                        + "event claim."
        );

        reasons.add(
                "All automatic verification requirements are satisfied."
        );

        reasons.add(
                "Final verification status: VERIFIED."
        );

        return new EventVerificationResult(
                VerificationStatus.VERIFIED,
                verifiedScore(
                        evidenceResult.verificationScore(),
                        claimResult.confidence()
                ),
                evidenceResult.normalizedSourceDomain(),
                reasons
        );
    }

    /**
     * Calculates the final score for a VERIFIED result.
     *
     * Evidence validation contributes 60%, while semantic confidence
     * contributes 40%.
     */
    private static double verifiedScore(
            double evidenceScore,
            double claimConfidence
    ) {
        return Math.min(
                1.0,
                evidenceScore * 0.60
                        + claimConfidence * 0.40
        );
    }

    /**
     * Keeps manual-review scores below the automatic verification
     * threshold.
     */
    private static double manualReviewScore(
            double evidenceScore,
            double additionalConfidence
    ) {
        return Math.min(
                0.74,
                evidenceScore * 0.50
                        + additionalConfidence * 0.25
        );
    }

    /**
     * Keeps rejected scores below 0.50.
     */
    private static double rejectionScore(
            double earlierScore,
            double claimConfidence
    ) {
        return Math.min(
                0.49,
                earlierScore * 0.25
                        + (1.0 - claimConfidence) * 0.25
        );
    }

    /**
     * Uses exact-domain and real-subdomain matching.
     *
     * Examples:
     *
     * admissions.ipu.ac.in is trusted for ipu.ac.in.
     * ipu.ac.in.attacker.example is not trusted.
     */
    private boolean isTrustedDomain(
            String sourceDomain
    ) {
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

        String value =
                sourceReference.trim();

        if ("UNSPECIFIED".equalsIgnoreCase(value)
                || "UNKNOWN".equalsIgnoreCase(value)) {
            return Optional.empty();
        }

        String candidate =
                value.contains("://")
                        ? value
                        : "https://" + value;

        try {
            URI uri =
                    new URI(candidate);

            String scheme =
                    uri.getScheme();

            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme))) {
                return Optional.empty();
            }

            /*
             * Reject user-info URL tricks such as:
             *
             * https://ipu.ac.in@attacker.example
             */
            if (uri.getUserInfo() != null) {
                return Optional.empty();
            }

            String host =
                    uri.getHost();

            if (host == null
                    || host.isBlank()) {
                return Optional.empty();
            }

            String normalized =
                    IDN.toASCII(host)
                            .toLowerCase(Locale.ROOT);

            while (normalized.endsWith(".")) {
                normalized =
                        normalized.substring(
                                0,
                                normalized.length() - 1
                        );
            }

            if (normalized.startsWith("www.")) {
                normalized =
                        normalized.substring(4);
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