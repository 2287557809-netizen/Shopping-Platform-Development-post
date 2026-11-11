package hu.unimiskolc.shopstream.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/carts")
@Validated
public class CartController {

    private static final String USER_ID_PATTERN = "[A-Za-z0-9_-]{1,40}";

    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping("/{userId}")
    public Cart get(@PathVariable @Pattern(regexp = USER_ID_PATTERN) String userId) {
        return service.get(userId);
    }

    @PostMapping("/{userId}/items")
    public Cart add(@PathVariable @Pattern(regexp = USER_ID_PATTERN) String userId,
                    @Valid @RequestBody AddCartItemRequest request) {
        return service.addItem(userId, request.productId(), request.quantity());
    }

    @PutMapping("/{userId}/items/{productId}")
    public Cart update(@PathVariable @Pattern(regexp = USER_ID_PATTERN) String userId,
                       @PathVariable Long productId,
                       @Valid @RequestBody UpdateCartItemRequest request) {
        return service.setQuantity(userId, productId, request.quantity());
    }

    @DeleteMapping("/{userId}/items/{productId}")
    public Cart remove(@PathVariable @Pattern(regexp = USER_ID_PATTERN) String userId,
                       @PathVariable Long productId) {
        return service.removeItem(userId, productId);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> clear(@PathVariable @Pattern(regexp = USER_ID_PATTERN) String userId) {
        service.clear(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @PathVariable @Pattern(regexp = USER_ID_PATTERN) String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.checkout(userId));
    }
}
