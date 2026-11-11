package hu.unimiskolc.shopstream.cart;

public class ProductServiceUnavailableException extends RuntimeException {
    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
