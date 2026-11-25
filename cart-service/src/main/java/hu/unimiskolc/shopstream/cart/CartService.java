package hu.unimiskolc.shopstream.cart;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private static final int MAX_QUANTITY = 99;

    private final CartRepository repository;
    private final ProductClient productClient;
    private final OrderClient orderClient;
    private final long timeToLiveSeconds;

    public CartService(CartRepository repository,
                       ProductClient productClient,
                       OrderClient orderClient,
                       @Value("${cart.time-to-live-seconds:1800}") long timeToLiveSeconds) {
        this.repository = repository;
        this.productClient = productClient;
        this.orderClient = orderClient;
        this.timeToLiveSeconds = timeToLiveSeconds;
    }

    public Cart get(String userId) {
        try {
            return repository.findById(userId).orElseGet(() -> new Cart(userId));
        } catch (DataAccessException ex) {
            throw new CartStorageException("Redis is unavailable", ex);
        }
    }

    public Cart addItem(String userId, Long productId, int quantity) {
        validateQuantity(quantity);
        ProductDto product = fetchProduct(productId);
        Cart cart = get(userId);

        CartItem item = cart.findItem(productId);
        int newQuantity = (item == null ? 0 : item.getQuantity()) + quantity;
        validateQuantity(newQuantity);
        checkStock(product, newQuantity);

        if (item == null) {
            item = new CartItem();
            item.setProductId(product.id());
            cart.getItems().add(item);
        }
        item.setName(product.name());
        item.setUnitPrice(product.price());
        item.setQuantity(newQuantity);

        cart.recalculate();
        save(cart);
        return cart;
    }

    public Cart setQuantity(String userId, Long productId, int quantity) {
        Cart cart = get(userId);
        CartItem item = cart.findItem(productId);
        if (item == null) {
            throw new CartItemNotFoundException(userId, productId);
        }

        if (quantity == 0) {
            cart.getItems().remove(item);
            cart.recalculate();
            save(cart);
            return cart;
        }

        validateQuantity(quantity);
        checkStock(fetchProduct(productId), quantity);
        item.setQuantity(quantity);
        cart.recalculate();
        save(cart);
        return cart;
    }

    public Cart removeItem(String userId, Long productId) {
        Cart cart = get(userId);
        cart.getItems().removeIf(item -> productId.equals(item.getProductId()));
        cart.recalculate();
        save(cart);
        return cart;
    }

    public void clear(String userId) {
        try {
            repository.findById(userId).ifPresent(repository::delete);
        } catch (DataAccessException ex) {
            throw new CartStorageException("Redis is unavailable", ex);
        }
    }

    /**
     * The cart is cleared only after order-service accepted the order. If the
     * checkout call fails the customer can simply retry with the same cart.
     */
    public OrderResponse checkout(String userId) {
        Cart cart = get(userId);
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException(userId);
        }

        List<OrderItemRequest> items = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            items.add(new OrderItemRequest(item.getProductId(), item.getQuantity()));
        }

        OrderResponse order;
        try {
            order = orderClient.create(new CreateOrderRequest(items));
        } catch (FeignException ex) {
            throw new OrderServiceUnavailableException("order-service rejected the checkout", ex);
        }

        clear(userId);
        log.info("Cart {} checked out as order {}", userId, order.id());
        return order;
    }

    private ProductDto fetchProduct(Long productId) {
        try {
            ProductDto product = productClient.getProduct(productId);
            if (product == null || product.price() == null) {
                throw new ProductNotFoundException(productId);
            }
            return product;
        } catch (FeignException.NotFound ex) {
            throw new ProductNotFoundException(productId);
        } catch (FeignException ex) {
            throw new ProductServiceUnavailableException("product-service is unavailable", ex);
        }
    }

    private void checkStock(ProductDto product, int quantity) {
        if (product.stock() != null && quantity > product.stock()) {
            throw new InvalidQuantityException("Only " + product.stock()
                    + " unit(s) of product " + product.id() + " are available");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InvalidQuantityException("Quantity must be positive");
        }
        if (quantity > MAX_QUANTITY) {
            throw new InvalidQuantityException("Quantity must not exceed " + MAX_QUANTITY);
        }
    }

    /**
     * The TTL is refreshed on every write, so the hash expires after 30 minutes
     * without activity. @TimeToLive reads this field when the entity is saved.
     */
    private void save(Cart cart) {
        cart.setTimeToLive(timeToLiveSeconds);
        try {
            repository.save(cart);
        } catch (DataAccessException ex) {
            throw new CartStorageException("Redis is unavailable", ex);
        }
    }
}
