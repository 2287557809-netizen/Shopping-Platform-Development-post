package hu.unimiskolc.shopstream.order;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the inventory result and moves the order from PENDING to
 * CONFIRMED or REJECTED. A duplicate result is ignored because the state
 * machine only accepts a transition from PENDING.
 */
@Component
public class InventoryEventsListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventsListener.class);

    private final OrderService orderService;

    public InventoryEventsListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "inventory-events", groupId = "order-service")
    public void onInventoryEvent(ConsumerRecord<String, EventMessage> record) {
        EventMessage event = record.value();
        if (event == null) {
            return;
        }
        log.info("Received {} key={} partition={} offset={}",
                event.eventType(), record.key(), record.partition(), record.offset());
        handleInventoryEvent(event);
    }

    void handleInventoryEvent(EventMessage event) {
        String eventType = event.eventType();
        if (!"StockReserved".equals(eventType) && !"StockRejected".equals(eventType)) {
            return;
        }

        JsonNode payload = event.payload();
        if (payload == null || !payload.path("orderId").canConvertToLong()) {
            log.error("Inventory event without orderId: {}", event);
            return;
        }

        Long orderId = payload.path("orderId").asLong();
        boolean reserved = "StockReserved".equals(eventType);
        String reason = payload.path("reason").asText(null);

        try {
            orderService.applyInventoryResult(orderId, reserved, reason);
        } catch (OrderNotFoundException ex) {
            // A late/duplicate event for a deleted order must not block the partition.
            log.warn("Ignoring inventory event for unknown order {}", orderId);
        }
    }
}
