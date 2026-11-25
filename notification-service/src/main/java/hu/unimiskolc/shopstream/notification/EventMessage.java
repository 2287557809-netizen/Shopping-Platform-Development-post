package hu.unimiskolc.shopstream.notification;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The JSON envelope carried by every topic this service consumes: eventId,
 * eventType, occurredAt and the event payload. JsonDeserializer maps the record
 * value onto this class.
 */
public record EventMessage(String eventId, String eventType, String occurredAt, JsonNode payload) {
}
