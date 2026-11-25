package hu.unimiskolc.shopstream.order;

import java.math.BigDecimal;
import java.util.List;

/**
 * Domain event raised by {@link OrderService} after an order row was stored as
 * PENDING. The transactional listener turns it into the wire event
 * {@code OrderPlaced}.
 */
public record OrderPlacedEvent(Long orderId, List<OrderItemPayload> items, BigDecimal totalPrice) {
}
