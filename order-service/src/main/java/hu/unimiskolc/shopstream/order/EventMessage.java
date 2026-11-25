package hu.unimiskolc.shopstream.order;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The JSON envelope of every Kafka record in this project: eventId, eventType,
 * occurredAt and the event-specific payload. JsonDeserializer maps the record
 * value onto this class; the listener picks the payload type from eventType.
 */
public record EventMessage(String eventId, String eventType, String occurredAt, JsonNode payload) {
}
