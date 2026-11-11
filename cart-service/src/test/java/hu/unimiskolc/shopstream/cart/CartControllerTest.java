package hu.unimiskolc.shopstream.cart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock
    private CartService service;

    @Test
    void delegatesAddItemToTheService() {
        CartController controller = new CartController(service);
        Cart cart = new Cart("alice");

        controller.add("alice", new AddCartItemRequest(1L, 2));

        verify(service).addItem("alice", 1L, 2);
    }

    @Test
    void delegatesCheckoutToTheService() {
        CartController controller = new CartController(service);

        controller.checkout("alice");

        verify(service).checkout("alice");
    }
}
