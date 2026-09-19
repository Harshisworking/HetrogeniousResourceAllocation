package com.harsh.cloudsim.agent.event;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

/**
 * Gemini-powered agent that converts an external real-world event
 * into a workload-impact assessment for the cloud-management system.
 */
public final class EventIntelligenceAgent {

    /**
     * Fixed model identifier makes experiments reproducible.
     */
    private static final String MODEL_NAME =
            "gemini-3.5-flash-lite";

    /**
     * ADK uses ROOT_AGENT as the entry point for running this agent.
     */
    public static final BaseAgent ROOT_AGENT =
            createAgent();

    private EventIntelligenceAgent() {
        /*
         * Utility class: instances are unnecessary because the ADK agent
         * is created once and exposed through ROOT_AGENT.
         */
    }

    private static BaseAgent createAgent() {
        return LlmAgent.builder()
                .name("event-intelligence-agent")
                .description(
                        "Analyses public events and estimates their impact "
                                + "on web-application traffic."
                )
                .instruction("""
                        You are the Event Intelligence Agent in an
                        event-aware cloud resource management system.

                        Your task is to analyse a real-world event announcement
                        and estimate its likely short-term effect on traffic to
                        the affected online service.

                        Examples include:
                        - university admission registration;
                        - examination-result announcements;
                        - application deadlines;
                        - ticket sales;
                        - government-service deadlines;
                        - emergency public notifications.

                        Return ONLY one valid JSON object.
                        Do not use Markdown.
                        Do not wrap the response in JSON code fences.
                        Do not include text before or after the JSON.

                        The JSON must contain exactly these fields:

                        {
                          "eventName": "short descriptive event name",
                          "eventType": "ADMISSION, RESULT, DEADLINE, TICKET_SALE, EMERGENCY, or OTHER",
                          "affectedService": "website or online service likely to receive traffic",
                          "expectedUsers": integer greater than or equal to 0,
                          "trafficMultiplier": number greater than or equal to 1.0,
                          "leadTimeMinutes": integer greater than or equal to 0,
                          "confidence": number between 0.0 and 1.0,
                          "reasoning": "short explanation of the traffic estimate"
                        }

                        Be conservative when information is missing.
                        Lower the confidence when the announcement is vague,
                        unverified or lacks timing and audience details.

                        Never invent a host ID.
                        Never directly allocate a VM.
                        """)
                .model(MODEL_NAME)
                .generateContentConfig(
                        GenerateContentConfig.builder()
                                .httpOptions(
                                        HttpOptions.builder()
                                                .retryOptions(
                                                        HttpRetryOptions
                                                                .builder()
                                                                .initialDelay(
                                                                        2.0
                                                                )
                                                                .attempts(3)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();
    }
}