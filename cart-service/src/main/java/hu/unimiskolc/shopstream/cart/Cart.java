package hu.unimiskolc.shopstream.cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The cart is stored as one JSON document in Redis (key cart:{userId}) with a
 * sliding TTL. A document is simpler to demo than a Redis Hash and keeps the
 * complete, price-snapshotted cart atomic.
 */
public class Cart {

    private String userId;
    private List<CartItem> items = new ArrayList<>();
    private BigDecimal totalPrice = BigDecimal.ZERO;
    private Instant updatedAt;

    public Cart() {
    }

    public Cart(String userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public void recalculate() {
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            BigDecimal line = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setLineTotal(line);
            total = total.add(line);
        }
        this.totalPrice = total;
        this.updatedAt = Instant.now();
    }

    public CartItem findItem(Long productId) {
        return items.stream()
                .filter(item -> productId.equals(item.getProductId()))
                .findFirst()
                .orElse(null);
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
