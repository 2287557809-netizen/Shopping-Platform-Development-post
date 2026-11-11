package hu.unimiskolc.shopstream.cart;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long productId) {
        super("Product not found: " + productId);
    }
}
