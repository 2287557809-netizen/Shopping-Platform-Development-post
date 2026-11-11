package hu.unimiskolc.shopstream.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Duration TTL = Duration.ofMinutes(30);

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProductClient productClient;

    @Mock
    private OrderClient orderClient;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private CartService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        service = new CartService(redis, objectMapper, productClient, orderClient, TTL);
    }

    @Test
    void returnsAnEmptyCartWhenRedisHasNoDocument() {
        when(valueOperations.get("cart:alice")).thenReturn(null);

        Cart cart = service.get("alice");

        assertThat(cart.getUserId()).isEqualTo("alice");
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("0");
    }

    @Test
    void addsAnItemAndPersistsAJsonDocumentWithTtl() throws Exception {
        when(valueOperations.get("cart:alice")).thenReturn(null);
        when(productClient.getProduct(1L)).thenReturn(
                new ProductDto(1L, "Laptop", new BigDecimal("100.00"), 5));

        Cart cart = service.addItem("alice", 1L, 2);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("200.00");

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("cart:alice"), json.capture(), eq(TTL));
        Cart persisted = objectMapper.readValue(json.getValue(), Cart.class);
        assertThat(persisted.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void refusesToAddMoreThanTheAvailableStock() {
        when(valueOperations.get("cart:alice")).thenReturn(null);
        when(productClient.getProduct(1L)).thenReturn(
                new ProductDto(1L, "Laptop", new BigDecimal("100.00"), 1));

        assertThatThrownBy(() -> service.addItem("alice", 1L, 2))
                .isInstanceOf(InvalidQuantityException.class)
                .hasMessageContaining("Only 1");
    }

    @Test
    void settingQuantityToZeroRemovesTheItem() throws Exception {
        Cart existing = cartWithLaptop();
        when(valueOperations.get("cart:alice"))
                .thenReturn(objectMapper.writeValueAsString(existing));

        Cart cart = service.setQuantity("alice", 1L, 0);

        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("0");
        verify(valueOperations).set(eq("cart:alice"), anyString(), eq(TTL));
    }

    @Test
    void checkoutCreatesAnOrderAndClearsTheCart() throws Exception {
        when(valueOperations.get("cart:alice"))
                .thenReturn(objectMapper.writeValueAsString(cartWithLaptop()));
        OrderResponse order = new OrderResponse(
                9L, "PENDING", new BigDecimal("200.00"),
                LocalDateTime.now(), LocalDateTime.now(), null,
                List.of(new OrderResponse.OrderItemResponse(
                        1L, 2, new BigDecimal("100.00"), new BigDecimal("200.00"))));
        when(orderClient.create(any(CreateOrderRequest.class))).thenReturn(order);

        OrderResponse response = service.checkout("alice");

        assertThat(response.id()).isEqualTo(9L);
        verify(orderClient).create(any(CreateOrderRequest.class));
        verify(redis).delete("cart:alice");
    }

    @Test
    void checkoutRejectsAnEmptyCart() {
        when(valueOperations.get("cart:alice")).thenReturn(null);

        assertThatThrownBy(() -> service.checkout("alice"))
                .isInstanceOf(EmptyCartException.class);
        verifyNoInteractions(orderClient);
    }

    @Test
    void discardsUnreadableCartJson() {
        when(valueOperations.get("cart:alice")).thenReturn("not-json");

        Cart cart = service.get("alice");

        assertThat(cart.getItems()).isEmpty();
        verify(redis).delete("cart:alice");
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
