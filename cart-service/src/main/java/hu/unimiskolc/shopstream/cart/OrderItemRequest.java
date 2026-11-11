package hu.unimiskolc.shopstream.cart;

public record OrderItemRequest(Long productId, Integer quantity) {
}
