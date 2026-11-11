package hu.unimiskolc.shopstream.product;

public record ProductChangedEvent(ProductChangeType type, Long productId, ProductPayload payload) {
}
