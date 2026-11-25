package hu.unimiskolc.shopstream.product;

import java.math.BigDecimal;
import java.util.List;

/**
 * The part of the OrderPlaced event that product-service needs: which order,
 * which products and how many units. Deserialised from the event payload.
 */
public record OrderPlacedPayload(Long orderId, List<OrderItemPayload> items, BigDecimal totalPrice) {
}
