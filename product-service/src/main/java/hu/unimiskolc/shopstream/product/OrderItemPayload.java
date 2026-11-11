package hu.unimiskolc.shopstream.product;

public record OrderItemPayload(Long productId, Integer quantity) {
}
