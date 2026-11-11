package hu.unimiskolc.shopstream.product;

import java.math.BigDecimal;

public record ProductPayload(
        Long id,
        String name,
        String description,
        String category,
        BigDecimal price,
        Integer stock) {

    public static ProductPayload from(Product product) {
        return new ProductPayload(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice(),
                product.getStock());
    }
}
