package hu.unimiskolc.shopstream.search;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The JSON envelope of the product-events records: eventId, eventType,
 * occurredAt and the product payload. JsonDeserializer maps the record value
 * onto this class.
 */
public record EventMessage(String eventId, String eventType, String occurredAt, JsonNode payload) {
}
