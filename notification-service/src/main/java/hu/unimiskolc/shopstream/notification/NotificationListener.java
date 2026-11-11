package hu.unimiskolc.shopstream.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final ObjectMapper objectMapper;
    private final NotificationStore store;

    public NotificationListener(ObjectMapper objectMapper, NotificationStore store) {
        this.objectMapper = objectMapper;
        this.store = store;
    }

    @Bean
    public Consumer<String> orderEvents() {
        return message -> handle(message, this::orderMessage);
    }

    @Bean
    public Consumer<String> inventoryEvents() {
        return message -> handle(message, this::inventoryMessage);
    }

    @Bean
    public Consumer<String> productEvents() {
        return message -> handle(message, this::productMessage);
    }

    private void handle(String message, MessageFactory factory) {
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (JsonProcessingException ex) {
            log.error("Dropping malformed event in notification-service: {}", message, ex);
            return;
        }

        String eventId = root.path("eventId").asText(null);
        String eventType = root.path("eventType").asText();
        JsonNode payload = root.path("payload");
        Long orderId = payload.path("orderId").canConvertToLong()
                ? payload.path("orderId").asLong()
                : null;

        String text = factory.create(eventType, payload);
        if (text == null) {
            return;
        }

        if (store.addIfNew(eventId, eventType, orderId, text)) {
            log.info("NOTIFICATION [{}] {}", eventType, text);
        }
    }

    private String orderMessage(String eventType, JsonNode payload) {
        Long orderId = payload.path("orderId").asLong();
        return switch (eventType) {
            case "OrderCreated" -> "Order #" + orderId + " received (total "
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

    private String productMessage(String eventType, JsonNode payload) {
        Long productId = payload.path("id").asLong();
        return switch (eventType) {
            case "ProductCreated" -> "Product #" + productId + " listed as '"
                    + payload.path("name").asText("?") + "'";
            case "ProductUpdated" -> "Product #" + productId + " updated";
            case "ProductDeleted" -> "Product #" + productId + " removed from catalogue";
            default -> null;
        };
    }

    @FunctionalInterface
    private interface MessageFactory {
        String create(String eventType, JsonNode payload);
    }
}
