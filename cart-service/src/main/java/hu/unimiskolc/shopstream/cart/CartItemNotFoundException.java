package hu.unimiskolc.shopstream.cart;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(String userId, Long productId) {
        super("Product " + productId + " is not in cart " + userId);
    }
}
