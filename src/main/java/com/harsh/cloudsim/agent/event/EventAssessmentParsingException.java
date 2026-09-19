package com.harsh.cloudsim.agent.event;

/**
 * Thrown when the Event Intelligence Agent returns a response that cannot
 * be converted into a valid event-impact assessment.
 */
public final class EventAssessmentParsingException
        extends IllegalArgumentException {

    public EventAssessmentParsingException(String message) {
        super(message);
    }

    public EventAssessmentParsingException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}