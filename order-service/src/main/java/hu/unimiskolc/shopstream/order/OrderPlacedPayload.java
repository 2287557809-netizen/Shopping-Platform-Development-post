package hu.unimiskolc.shopstream.order;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload of the OrderPlaced event on order-events (Step 2). product-service
 * reads it to reserve stock; the orderId is also the Kafka message key.
 */
public record OrderPlacedPayload(Long orderId, List<OrderItemPayload> items, BigDecimal totalPrice) {
}
