package hu.unimiskolc.shopstream.order;

public record OrderStatusChangedEvent(Long orderId, OrderStatus status, String reason) {
}
