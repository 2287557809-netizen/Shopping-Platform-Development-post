package hu.unimiskolc.shopstream.cart;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 4, task 2: the cart is a Redis hash.
 *
 * <p>{@link RedisHash} maps the entity to keys of the form {@code carts:{userId}},
 * {@link Id} is the userId, and {@link TimeToLive} makes the whole hash expire
 * without any manual {@code EXPIRE} call. The TTL is refreshed on every save,
 * so an active shopper keeps the cart while an abandoned one loses it.</p>
 */
@RedisHash("carts")
public class Cart implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String userId;

    /** Expiry in seconds, maintained by Spring Data Redis. */
    @TimeToLive
    private Long timeToLive;

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

    public Long getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(Long timeToLive) {
        this.timeToLive = timeToLive;
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
