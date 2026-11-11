package hu.unimiskolc.shopstream.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes the common JSON envelope through Spring Cloud Stream.
 *
 * <p>The payload is deliberately sent as a JSON string with a text/plain content
 * type. Each consumer owns its own event DTO, so the services stay decoupled and
 * no Java class name has to travel through Kafka headers.</p>
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;

    public EventPublisher(StreamBridge streamBridge, ObjectMapper objectMapper) {
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
    }

    public void publish(String binding, String key, String eventType, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize event " + eventType, ex);
        }

        Message<String> message = MessageBuilder.withPayload(json)
                .setHeader(KafkaHeaders.KEY, key)
                .setHeader("contentType", MimeTypeUtils.TEXT_PLAIN_VALUE)
                .build();

        boolean sent = streamBridge.send(binding, message);
        log.info("Published {} key={} binding={} sent={}", eventType, key, binding, sent);
    }
}
