package hu.unimiskolc.shopstream.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes events with {@link KafkaTemplate}.
 *
 * <p>Step 2, task 3: the JSON value is produced by JsonSerializer and the key is
 * the orderId, so OrderPlaced / OrderConfirmed / OrderRejected for the same
 * order always land in the same partition and are consumed in order.</p>
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String key, String eventType, Object payload) {
        JsonNode body = payload == null ? null : objectMapper.valueToTree(payload);
        EventMessage event = new EventMessage(
                UUID.randomUUID().toString(), eventType, Instant.now().toString(), body);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
        future.whenComplete((result, error) -> {
            if (error != null) {
                log.error("Failed to publish {} key={} to {}", eventType, key, topic, error);
            } else if (result != null) {
                log.info("Published {} key={} to {} partition={} offset={}",
                        eventType, key, topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
