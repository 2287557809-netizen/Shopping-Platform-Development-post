package hu.unimiskolc.shopstream.cart;

import java.math.BigDecimal;

public record ProductDto(Long id, String name, BigDecimal price, Integer stock) {
}
