package hu.unimiskolc.shopstream.order;

public record OrderItemPayload(Long productId, Integer quantity) {
}
