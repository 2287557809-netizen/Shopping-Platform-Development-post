package hu.unimiskolc.shopstream.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class InventoryEventsListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventsListener.class);

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    public InventoryEventsListener(ObjectMapper objectMapper, OrderService orderService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
    }

    @Bean
    public Consumer<String> inventoryEvents() {
        return this::handleInventoryEvent;
    }

    void handleInventoryEvent(String message) {
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (JsonProcessingException ex) {
            log.error("Dropping malformed inventory event: {}", message, ex);
            return;
        }

        String eventType = root.path("eventType").asText();
        if (!"StockReserved".equals(eventType) && !"StockRejected".equals(eventType)) {
            return;
        }

        JsonNode payload = root.path("payload");
        if (!payload.path("orderId").canConvertToLong()) {
            log.error("Inventory event without orderId: {}", message);
            return;
        }

        Long orderId = payload.path("orderId").asLong();
        boolean reserved = "StockReserved".equals(eventType);
        String reason = payload.path("reason").asText(null);

        try {
            orderService.applyInventoryResult(orderId, reserved, reason);
        } catch (OrderNotFoundException ex) {
            // A late/duplicate event for a deleted order should not block the partition.
            log.warn("Ignoring inventory event for unknown order {}", orderId);
        }
    }
}
