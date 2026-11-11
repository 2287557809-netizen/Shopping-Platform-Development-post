package hu.unimiskolc.shopstream.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Consumes OrderCreated and turns it into a stock reservation. The result is
 * published to inventory-events where order-service and notification-service
 * consume it.
 */
@Configuration
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final ObjectMapper objectMapper;
    private final ReservationService reservationService;
    private final EventPublisher eventPublisher;

    public OrderEventsListener(ObjectMapper objectMapper,
                               ReservationService reservationService,
                               EventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.reservationService = reservationService;
        this.eventPublisher = eventPublisher;
    }

    @Bean
    public Consumer<String> orderEvents() {
        return this::handleOrderEvent;
    }

    void handleOrderEvent(String message) {
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (JsonProcessingException ex) {
            log.error("Dropping malformed order event: {}", message, ex);
            return;
        }

        String eventType = root.path("eventType").asText();
        if (!"OrderCreated".equals(eventType)) {
            return;
        }

        OrderCreatedPayload payload;
        try {
            payload = objectMapper.treeToValue(root.path("payload"), OrderCreatedPayload.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.error("Dropping invalid OrderCreated payload: {}", root.path("payload"), ex);
            return;
        }

        ReservationResult result;
        try {
            result = reservationService.reserve(payload);
        } catch (InsufficientStockException ex) {
            log.warn("Stock reservation failed for order {}: {}", payload.orderId(), ex.getMessage());
            result = reservationService.reject(payload.orderId(), ex.getMessage());
        }

        String eventName = result.reserved() ? "StockReserved" : "StockRejected";
        eventPublisher.publish("inventoryEvents-out-0", String.valueOf(result.orderId()), eventName, result);
    }
}
