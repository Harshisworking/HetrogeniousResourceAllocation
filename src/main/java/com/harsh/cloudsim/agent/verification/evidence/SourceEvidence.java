package com.harsh.cloudsim.agent.verification.evidence;

import java.time.Instant;
import java.util.Objects;

/**
 * Evidence retrieved from an external source webpage.
 *
 * @param requestedSource original URL/domain supplied for verification
 * @param finalUrl        final URL after safe redirects
 * @param status          evidence-retrieval status
 * @param httpStatusCode  HTTP response code, or 0 when no response exists
 * @param retrievedAt     retrieval timestamp
 * @param contentType     response content type
 * @param pageTitle       extracted HTML page title
 * @param visibleText     visible text extracted from the page
 * @param details         explanation of the retrieval result
 */
public record SourceEvidence(
        String requestedSource,
        String finalUrl,
        EvidenceFetchStatus status,
        int httpStatusCode,
        Instant retrievedAt,
        String contentType,
        String pageTitle,
        String visibleText,
        String details
) {

    public SourceEvidence {
        if (requestedSource == null
                || requestedSource.isBlank()) {
            throw new IllegalArgumentException(
                    "Requested source must not be blank."
            );
        }

        if (finalUrl == null || finalUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Final URL must not be blank."
            );
        }

        Objects.requireNonNull(
                status,
                "Evidence-fetch status must not be null."
        );

        if (httpStatusCode < 0
                || httpStatusCode > 599) {
            throw new IllegalArgumentException(
                    "HTTP status code must be between 0 and 599."
            );
        }

        Objects.requireNonNull(
                retrievedAt,
                "Retrieval time must not be null."
        );

        Objects.requireNonNull(
                contentType,
                "Content type must not be null."
        );

        Objects.requireNonNull(
                pageTitle,
                "Page title must not be null."
        );

        Objects.requireNonNull(
                visibleText,
                "Visible text must not be null."
        );

        if (details == null || details.isBlank()) {
            throw new IllegalArgumentException(
                    "Evidence details must not be blank."
            );
        }

        if (status == EvidenceFetchStatus.FETCHED) {
            if (httpStatusCode < 200
                    || httpStatusCode >= 300) {
                throw new IllegalArgumentException(
                        "Fetched evidence requires a successful "
                                + "HTTP status code."
                );
            }

            if (visibleText.isBlank()) {
                throw new IllegalArgumentException(
                        "Fetched evidence must contain visible text."
                );
            }

            if ("unknown".equalsIgnoreCase(finalUrl)) {
                throw new IllegalArgumentException(
                        "Fetched evidence must contain a final URL."
                );
            }
        }

        requestedSource = requestedSource.trim();
        finalUrl = finalUrl.trim();
        contentType = contentType.trim();
        pageTitle = pageTitle.trim();
        visibleText = visibleText.trim();
        details = details.trim();
    }

    public static SourceEvidence fetched(
            String requestedSource,
            String finalUrl,
            int httpStatusCode,
            String contentType,
            String pageTitle,
            String visibleText
    ) {
        return new SourceEvidence(
                requestedSource,
                finalUrl,
                EvidenceFetchStatus.FETCHED,
                httpStatusCode,
                Instant.now(),
                contentType,
                pageTitle,
                visibleText,
                "Source webpage was fetched successfully."
        );
    }

    public static SourceEvidence failure(
            String requestedSource,
            String finalUrl,
            EvidenceFetchStatus status,
            int httpStatusCode,
            String details
    ) {
        if (status == EvidenceFetchStatus.FETCHED) {
            throw new IllegalArgumentException(
                    "Failure factory cannot create FETCHED evidence."
            );
        }

        return new SourceEvidence(
                requestedSource,
                finalUrl,
                status,
                httpStatusCode,
                Instant.now(),
                "",
                "",
                "",
                details
        );
    }

    public boolean wasFetched() {
        return status == EvidenceFetchStatus.FETCHED;
    }
}