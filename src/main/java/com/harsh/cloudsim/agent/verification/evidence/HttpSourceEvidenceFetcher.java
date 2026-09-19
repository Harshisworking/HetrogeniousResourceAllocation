package com.harsh.cloudsim.agent.verification.evidence;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Secure HTTP implementation of SourceEvidenceFetcher.
 *
 * Only explicitly trusted HTTPS domains are allowed. Redirects are
 * processed manually so every redirect target can be checked.
 */
public final class HttpSourceEvidenceFetcher
        implements SourceEvidenceFetcher {

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_BODY_BYTES = 1_000_000;

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(8);

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(12);

    private static final String USER_AGENT =
            "EventAwareCloudVerifier/1.0 "
                    + "(academic cloud-management project)";

    private static final Set<String> DEFAULT_TRUSTED_DOMAINS =
            Set.of(
                    "ipu.ac.in",
                    "nta.ac.in",
                    "cbse.gov.in",
                    "india.gov.in",
                    "irctc.co.in"
            );

    private final HttpClient httpClient;
    private final Set<String> trustedDomains;

    public HttpSourceEvidenceFetcher() {
        this(DEFAULT_TRUSTED_DOMAINS);
    }

    public HttpSourceEvidenceFetcher(
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

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(
                        HttpClient.Redirect.NEVER
                )
                .build();
    }

    @Override
    public SourceEvidence fetch(String sourceReference) {
        String displayedSource =
                displaySource(sourceReference);

        Optional<URI> requestedUri =
                normalizeSourceUri(sourceReference);

        if (requestedUri.isEmpty()) {
            return SourceEvidence.failure(
                    displayedSource,
                    "unknown",
                    EvidenceFetchStatus.BLOCKED,
                    0,
                    "Source is not a valid HTTPS URL."
            );
        }

        URI originalUri = requestedUri.get();
        URI currentUri = originalUri;

        for (int redirectCount = 0;
             redirectCount <= MAX_REDIRECTS;
             redirectCount++) {

            if (!isAllowedUri(currentUri)) {
                return SourceEvidence.failure(
                        displayedSource,
                        currentUri.toString(),
                        EvidenceFetchStatus.BLOCKED,
                        0,
                        "URL is not permitted by the trusted-domain "
                                + "and HTTPS policy."
                );
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(currentUri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header(
                            "Accept",
                            "text/html,application/xhtml+xml,"
                                    + "text/plain;q=0.8"
                    )
                    .GET()
                    .build();

            try {
                HttpResponse<InputStream> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers
                                        .ofInputStream()
                        );

                int statusCode = response.statusCode();

                try (InputStream responseBody =
                             response.body()) {

                    if (isRedirect(statusCode)) {
                        Optional<String> location =
                                response.headers()
                                        .firstValue("Location");

                        if (location.isEmpty()) {
                            return SourceEvidence.failure(
                                    displayedSource,
                                    currentUri.toString(),
                                    EvidenceFetchStatus.UNREACHABLE,
                                    statusCode,
                                    "Redirect response did not contain "
                                            + "a Location header."
                            );
                        }

                        if (redirectCount
                                == MAX_REDIRECTS) {
                            return SourceEvidence.failure(
                                    displayedSource,
                                    currentUri.toString(),
                                    EvidenceFetchStatus
                                            .TOO_MANY_REDIRECTS,
                                    statusCode,
                                    "Source exceeded the maximum "
                                            + "redirect limit."
                            );
                        }

                        currentUri = currentUri.resolve(
                                location.get()
                        );

                        continue;
                    }

                    if (statusCode == 404) {
                        return SourceEvidence.failure(
                                displayedSource,
                                currentUri.toString(),
                                EvidenceFetchStatus.NOT_FOUND,
                                statusCode,
                                "Source webpage was not found."
                        );
                    }

                    if (statusCode < 200
                            || statusCode >= 300) {
                        return SourceEvidence.failure(
                                displayedSource,
                                currentUri.toString(),
                                EvidenceFetchStatus.UNREACHABLE,
                                statusCode,
                                "Source returned HTTP status "
                                        + statusCode
                                        + "."
                        );
                    }

                    String contentType =
                            response.headers()
                                    .firstValue("Content-Type")
                                    .orElse("");

                    if (!isSupportedContentType(
                            contentType
                    )) {
                        return SourceEvidence.failure(
                                displayedSource,
                                currentUri.toString(),
                                EvidenceFetchStatus
                                        .UNSUPPORTED_CONTENT,
                                statusCode,
                                "Unsupported content type: "
                                        + contentType
                        );
                    }

                    byte[] responseBytes =
                            responseBody.readNBytes(
                                    MAX_BODY_BYTES + 1
                            );

                    if (responseBytes.length
                            > MAX_BODY_BYTES) {
                        return SourceEvidence.failure(
                                displayedSource,
                                currentUri.toString(),
                                EvidenceFetchStatus
                                        .CONTENT_TOO_LARGE,
                                statusCode,
                                "Source content exceeded "
                                        + MAX_BODY_BYTES
                                        + " bytes."
                        );
                    }

                    return parseEvidence(
                            displayedSource,
                            currentUri,
                            statusCode,
                            contentType,
                            responseBytes
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                return SourceEvidence.failure(
                        displayedSource,
                        currentUri.toString(),
                        EvidenceFetchStatus.UNREACHABLE,
                        0,
                        "Evidence retrieval was interrupted."
                );
            } catch (IOException
                     | IllegalArgumentException exception) {
                return SourceEvidence.failure(
                        displayedSource,
                        currentUri.toString(),
                        EvidenceFetchStatus.UNREACHABLE,
                        0,
                        "Unable to retrieve source: "
                                + safeMessage(exception)
                );
            }
        }

        return SourceEvidence.failure(
                displayedSource,
                currentUri.toString(),
                EvidenceFetchStatus.TOO_MANY_REDIRECTS,
                0,
                "Source exceeded the maximum redirect limit."
        );
    }

    private SourceEvidence parseEvidence(
            String requestedSource,
            URI finalUri,
            int statusCode,
            String contentType,
            byte[] responseBytes
    ) throws IOException {
        Document document;

        if (contentType
                .toLowerCase(Locale.ROOT)
                .contains("text/plain")) {
            String text = new String(
                    responseBytes,
                    StandardCharsets.UTF_8
            );

            text = normalizeWhitespace(text);

            if (text.isBlank()) {
                return SourceEvidence.failure(
                        requestedSource,
                        finalUri.toString(),
                        EvidenceFetchStatus
                                .UNSUPPORTED_CONTENT,
                        statusCode,
                        "Source did not contain readable text."
                );
            }

            return SourceEvidence.fetched(
                    requestedSource,
                    finalUri.toString(),
                    statusCode,
                    contentType,
                    "",
                    text
            );
        }

        try (ByteArrayInputStream input =
                     new ByteArrayInputStream(
                             responseBytes
                     )) {
            document = Jsoup.parse(
                    input,
                    null,
                    finalUri.toString()
            );
        }

        document.select(
                "script, style, noscript, svg, canvas"
        ).remove();

        String pageTitle =
                normalizeWhitespace(document.title());

        String visibleText =
                normalizeWhitespace(
                        document.body().text()
                );

        if (visibleText.isBlank()) {
            return SourceEvidence.failure(
                    requestedSource,
                    finalUri.toString(),
                    EvidenceFetchStatus
                            .UNSUPPORTED_CONTENT,
                    statusCode,
                    "Source did not contain readable visible text."
            );
        }

        return SourceEvidence.fetched(
                requestedSource,
                finalUri.toString(),
                statusCode,
                contentType,
                pageTitle,
                visibleText
        );
    }

    private boolean isAllowedUri(URI uri) {
        if (!"https".equalsIgnoreCase(
                uri.getScheme()
        )) {
            return false;
        }

        if (uri.getUserInfo() != null) {
            return false;
        }

        if (uri.getPort() != -1
                && uri.getPort() != 443) {
            return false;
        }

        Optional<String> normalizedHost =
                normalizeDomain(uri.getHost());

        return normalizedHost
                .map(this::isTrustedDomain)
                .orElse(false);
    }

    private boolean isTrustedDomain(String sourceDomain) {
        return trustedDomains.stream().anyMatch(
                trustedDomain ->
                        sourceDomain.equals(trustedDomain)
                                || sourceDomain.endsWith(
                                "." + trustedDomain
                        )
        );
    }

    private static Optional<URI> normalizeSourceUri(
            String sourceReference
    ) {
        if (sourceReference == null
                || sourceReference.isBlank()) {
            return Optional.empty();
        }

        String value = sourceReference.trim();

        String candidate = value.contains("://")
                ? value
                : "https://" + value;

        try {
            URI uri = new URI(candidate);

            if (!uri.isAbsolute()
                    || uri.getHost() == null) {
                return Optional.empty();
            }

            return Optional.of(uri);
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> normalizeDomain(
            String sourceReference
    ) {
        if (sourceReference == null
                || sourceReference.isBlank()) {
            return Optional.empty();
        }

        String value = sourceReference.trim();

        String candidate = value.contains("://")
                ? value
                : "https://" + value;

        try {
            URI uri = new URI(candidate);
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

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static boolean isSupportedContentType(
            String contentType
    ) {
        String normalized =
                contentType.toLowerCase(Locale.ROOT);

        return normalized.contains("text/html")
                || normalized.contains(
                "application/xhtml+xml"
        )
                || normalized.contains("text/plain");
    }

    private static String normalizeWhitespace(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\s+", " ")
                .trim();
    }

    private static String displaySource(
            String sourceReference
    ) {
        if (sourceReference == null) {
            return "<null>";
        }

        if (sourceReference.isBlank()) {
            return "<blank>";
        }

        return sourceReference.trim();
    }

    private static String safeMessage(
            Throwable throwable
    ) {
        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            return throwable.getClass()
                    .getSimpleName();
        }

        return message;
    }
}