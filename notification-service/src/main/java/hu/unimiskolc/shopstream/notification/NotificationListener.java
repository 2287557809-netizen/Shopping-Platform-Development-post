package hu.unimiskolc.shopstream.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Step 2, task 7: consumer group notification-service on order-events and
 * inventory-events. Each final order status produces one "email" line in the
 * log. The store and the REST controller only exist to make the asynchronous
 * flow observable during the demo.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationStore store;

    public NotificationListener(NotificationStore store) {
        this.store = store;
    }

    @KafkaListener(topics = "${app.topics.order-events:order-events}", groupId = "notification-service")
    public void onOrderEvent(ConsumerRecord<String, EventMessage> record) {
        if (record.value() == null) {
            return;
        }
        log.info("Received {} key={} partition={} offset={}",
                record.value().eventType(), record.key(), record.partition(), record.offset());
        handle(record.value(), this::orderMessage);
    }

    @KafkaListener(topics = "${app.topics.inventory-events:inventory-events}", groupId = "notification-service")
    public void onInventoryEvent(ConsumerRecord<String, EventMessage> record) {
        if (record.value() == null) {
            return;
        }
        log.info("Received {} key={} partition={} offset={}",
                record.value().eventType(), record.key(), record.partition(), record.offset());
        handle(record.value(), this::inventoryMessage);
    }

    void handle(EventMessage event, MessageFactory factory) {
        JsonNode payload = event.payload();
        if (payload == null) {
            return;
        }
        Long orderId = payload.path("orderId").canConvertToLong()
                ? payload.path("orderId").asLong()
                : null;

        String text = factory.create(event.eventType(), payload);
        if (text == null) {
            return;
        }

        if (store.addIfNew(event.eventId(), event.eventType(), orderId, text)) {
            log.info("NOTIFICATION [{}] {}", event.eventType(), text);
        }
    }

    private String orderMessage(String eventType, JsonNode payload) {
        Long orderId = payload.path("orderId").asLong();
        return switch (eventType) {
            case "OrderPlaced" -> "Order #" + orderId + " received (total "
                    + payload.path("totalPrice").asText("?") + "), waiting for stock";
            case "OrderConfirmed" -> "Order #" + orderId + " confirmed by inventory";
            case "OrderRejected" -> "Order #" + orderId + " rejected: "
                    + payload.path("reason").asText("stock reservation failed");
            default -> null;
        };
    }

    private String inventoryMessage(String eventType, JsonNode payload) {
        Long orderId = payload.path("orderId").asLong();
        return switch (eventType) {
            case "StockReserved" -> "Stock reserved for order #" + orderId;
            case "StockRejected" -> "Stock rejected for order #" + orderId + ": "
                    + payload.path("reason").asText("unknown reason");
            default -> null;
        };
    }

    @FunctionalInterface
    private interface MessageFactory {
        String create(String eventType, JsonNode payload);
    }
}
