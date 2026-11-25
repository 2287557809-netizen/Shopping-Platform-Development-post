package hu.unimiskolc.shopstream.cart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final long TTL_SECONDS = 1800L;

    @Mock
    private CartRepository repository;

    @Mock
    private ProductClient productClient;

    @Mock
    private OrderClient orderClient;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(repository, productClient, orderClient, TTL_SECONDS);
    }

    @Test
    void returnsAnEmptyCartWhenRedisHasNoEntry() {
        when(repository.findById("alice")).thenReturn(Optional.empty());

        Cart cart = service.get("alice");

        assertThat(cart.getUserId()).isEqualTo("alice");
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("0");
    }

    @Test
    void readsTheCartFromTheRedisHash() {
        Cart stored = cartWithLaptop();
        when(repository.findById("alice")).thenReturn(Optional.of(stored));

        Cart cart = service.get("alice");

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void addsAnItemAndSavesTheHashWithExpiry() {
        when(repository.findById("alice")).thenReturn(Optional.empty());
        when(productClient.getProduct(1L)).thenReturn(
                new ProductDto(1L, "Laptop", new BigDecimal("100.00"), 5));

        Cart cart = service.addItem("alice", 1L, 2);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("200.00");

        ArgumentCaptor<Cart> saved = ArgumentCaptor.forClass(Cart.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTimeToLive()).isEqualTo(TTL_SECONDS);
        assertThat(saved.getValue().getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(saved.getValue().getUserId()).isEqualTo("alice");
    }

    @Test
    void refusesToAddMoreThanTheAvailableStock() {
        when(repository.findById("alice")).thenReturn(Optional.empty());
        when(productClient.getProduct(1L)).thenReturn(
                new ProductDto(1L, "Laptop", new BigDecimal("100.00"), 1));

        assertThatThrownBy(() -> service.addItem("alice", 1L, 2))
                .isInstanceOf(InvalidQuantityException.class)
                .hasMessageContaining("Only 1");

        verifyNoInteractions(orderClient);
    }

    @Test
    void settingQuantityToZeroRemovesTheItem() {
        Cart existing = cartWithLaptop();
        when(repository.findById("alice")).thenReturn(Optional.of(existing));

        Cart cart = service.setQuantity("alice", 1L, 0);

        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("0");
        verify(repository).save(any(Cart.class));
    }

    @Test
    void checkoutCreatesAnOrderAndDeletesTheCart() {
        Cart existing = cartWithLaptop();
        when(repository.findById("alice")).thenReturn(Optional.of(existing));
        OrderResponse order = new OrderResponse(
                9L, "PENDING", new BigDecimal("200.00"),
                LocalDateTime.now(), LocalDateTime.now(), null,
                List.of(new OrderResponse.OrderItemResponse(
                        1L, 2, new BigDecimal("100.00"), new BigDecimal("200.00"))));
        when(orderClient.create(any(CreateOrderRequest.class))).thenReturn(order);

        OrderResponse response = service.checkout("alice");

        assertThat(response.id()).isEqualTo(9L);
        verify(orderClient).create(any(CreateOrderRequest.class));
        verify(repository).delete(existing);
    }

    @Test
    void checkoutRejectsAnEmptyCart() {
        when(repository.findById("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout("alice"))
                .isInstanceOf(EmptyCartException.class);
        verifyNoInteractions(orderClient);
    }

    private Cart cartWithLaptop() {
        Cart cart = new Cart("alice");
        CartItem item = new CartItem();
        item.setProductId(1L);
        item.setName("Laptop");
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setQuantity(2);
        cart.getItems().add(item);
        cart.recalculate();
        return cart;
    }
}
