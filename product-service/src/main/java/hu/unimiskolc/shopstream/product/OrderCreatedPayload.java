package hu.unimiskolc.shopstream.product;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedPayload(Long orderId, List<OrderItemPayload> items, BigDecimal totalPrice) {
}
