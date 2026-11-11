package hu.unimiskolc.shopstream.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateCartItemRequest(
        @NotNull @PositiveOrZero Integer quantity) {
}
