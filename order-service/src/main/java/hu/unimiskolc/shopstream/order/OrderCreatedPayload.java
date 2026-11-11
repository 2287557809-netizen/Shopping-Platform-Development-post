package hu.unimiskolc.shopstream.order;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedPayload(Long orderId, List<OrderItemPayload> items, BigDecimal totalPrice) {
}
