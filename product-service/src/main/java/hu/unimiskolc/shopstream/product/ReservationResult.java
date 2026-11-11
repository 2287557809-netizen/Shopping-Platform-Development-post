package hu.unimiskolc.shopstream.product;

import java.util.List;

public record ReservationResult(Long orderId, String status, List<ReservedItem> items, String reason) {

    public record ReservedItem(Long productId, Integer quantity, Integer remainingStock) {
    }

    public boolean reserved() {
        return "RESERVED".equals(status);
    }
}
