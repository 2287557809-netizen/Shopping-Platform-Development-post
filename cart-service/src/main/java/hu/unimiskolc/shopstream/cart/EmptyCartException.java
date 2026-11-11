package hu.unimiskolc.shopstream.cart;

public class EmptyCartException extends RuntimeException {
    public EmptyCartException(String userId) {
        super("Cart is empty: " + userId);
    }
}
