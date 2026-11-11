package hu.unimiskolc.shopstream.cart;

public class CartStorageException extends RuntimeException {
    public CartStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
