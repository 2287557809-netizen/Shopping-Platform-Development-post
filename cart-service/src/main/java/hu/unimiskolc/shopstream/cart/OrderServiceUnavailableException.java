package hu.unimiskolc.shopstream.cart;

public class OrderServiceUnavailableException extends RuntimeException {
    public OrderServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
