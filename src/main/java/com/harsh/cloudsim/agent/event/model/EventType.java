package com.harsh.cloudsim.agent.event.model;

/**
 * Supported categories of real-world events that may affect
 * cloud-application traffic.
 */
public enum EventType {

    /**
     * Admission registration, counselling or application opening.
     */
    ADMISSION,

    /**
     * Examination, admission or recruitment result announcement.
     */
    RESULT,

    /**
     * Application, payment or submission deadline.
     */
    DEADLINE,

    /**
     * Online sale of tickets for transport, entertainment or events.
     */
    TICKET_SALE,

    /**
     * Emergency notification or urgent public-service event.
     */
    EMERGENCY,

    /**
     * Event that does not match one of the known categories.
     */
    OTHER
}