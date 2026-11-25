package hu.unimiskolc.shopstream.product;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The JSON envelope carried in the value of every Kafka record:
 * {@code eventId}, {@code eventType}, {@code occurredAt} and the event-specific
 * {@code payload}. JsonDeserializer maps the record value onto this class, and
 * the listener decides from {@code eventType} which payload to read.
 */
public record EventMessage(String eventId, String eventType, String occurredAt, JsonNode payload) {
}
