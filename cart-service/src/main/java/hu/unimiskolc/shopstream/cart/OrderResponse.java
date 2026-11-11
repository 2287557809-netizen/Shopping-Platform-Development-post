package hu.unimiskolc.shopstream.cart;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal totalPrice,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String failureReason,
        List<OrderItemResponse> items) {

    public record OrderItemResponse(
            Long productId,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }
}
