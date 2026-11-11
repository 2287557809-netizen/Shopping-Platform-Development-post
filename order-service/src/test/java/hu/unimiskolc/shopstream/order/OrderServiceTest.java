package hu.unimiskolc.shopstream.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private ProductClient productClient;

    @Mock
    private ApplicationEventPublisher events;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(repository, productClient, events);
    }

    @Test
    void createsAnOrderUsingThePricesFromProductService() {
        when(productClient.getProduct(1L)).thenReturn(new ProductDto(
                1L, "Laptop", new BigDecimal("100.00"), 5));
        when(productClient.getProduct(2L)).thenReturn(new ProductDto(
                2L, "Mouse", new BigDecimal("10.00"), 5));
        when(repository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(55L);
            return order;
        });

        OrderResponse response = service.create(new CreateOrderRequest(List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 3))));

        assertThat(response.id()).isEqualTo(55L);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalPrice()).isEqualByComparingTo("230.00");
        assertThat(response.items()).hasSize(2);
        verify(events).publishEvent(any(OrderCreatedEvent.class));
    }

    @Test
    void rejectsAnEmptyOrderBeforeCallingProductService() {
        assertThatThrownBy(() -> service.create(new CreateOrderRequest(List.of())))
                .isInstanceOf(InvalidOrderException.class);
        verifyNoInteractions(productClient);
        verifyNoInteractions(events);
    }

    @Test
    void confirmsAPendingOrderOnlyOnce() {
        Order order = pendingOrder(77L);
        when(repository.findById(77L)).thenReturn(Optional.of(order));
        when(repository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.applyInventoryResult(77L, true, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(events).publishEvent(any(OrderStatusChangedEvent.class));

        service.applyInventoryResult(77L, true, null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(events).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void recordsTheRejectionReason() {
        Order order = pendingOrder(78L);
        when(repository.findById(78L)).thenReturn(Optional.of(order));
        when(repository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.applyInventoryResult(78L, false, "Not enough stock");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getFailureReason()).isEqualTo("Not enough stock");
        verify(events).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void ignoresInventoryResultsForUnknownOrders() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyInventoryResult(999L, true, null))
                .isInstanceOf(OrderNotFoundException.class);
        verify(repository, never()).save(any());
    }

    private Order pendingOrder(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalPrice(new BigDecimal("10.00"));
        OrderItem item = new OrderItem();
        item.setProductId(1L);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("10.00"));
        order.addItem(item);
        return order;
    }
}
