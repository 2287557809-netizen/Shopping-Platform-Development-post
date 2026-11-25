package hu.unimiskolc.shopstream.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Step 2, task 5: consumes OrderPlaced from order-events in consumer group
 * product-service, reserves the stock in one database transaction and publishes
 * StockReserved or StockRejected to inventory-events.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final ObjectMapper objectMapper;
    private final ReservationService reservationService;
    private final EventPublisher eventPublisher;

    @Value("${app.topics.inventory-events:inventory-events}")
    private String inventoryEventsTopic;

    public OrderEventsListener(ObjectMapper objectMapper,
                               ReservationService reservationService,
                               EventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.reservationService = reservationService;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(topics = "${app.topics.order-events:order-events}", groupId = "product-service")
    public void onOrderEvent(ConsumerRecord<String, EventMessage> record) {
        EventMessage event = record.value();
        if (event == null) {
            return;
        }
        if (!"OrderPlaced".equals(event.eventType())) {
            return;
        }
        log.info("Received {} key={} partition={} offset={}",
                event.eventType(), record.key(), record.partition(), record.offset());

        if (event.payload() == null) {
            log.error("OrderPlaced event without payload: key={}", record.key());
            return;
        }

        OrderPlacedPayload payload;
        try {
            payload = objectMapper.treeToValue(event.payload(), OrderPlacedPayload.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.error("Dropping invalid OrderPlaced payload: {}", event.payload(), ex);
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
        eventPublisher.publish(inventoryEventsTopic, String.valueOf(result.orderId()), eventName, result);
    }
}
