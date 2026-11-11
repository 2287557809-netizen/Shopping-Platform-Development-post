package hu.unimiskolc.shopstream.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockReservationRepository reservationRepository;

    private ReservationService service;

    private void setUpService() {
        service = new ReservationService(productRepository, reservationRepository);
    }

    @Test
    void reservesStockAndLocksProductsInIdOrder() {
        setUpService();
        Product first = product(1L, 5);
        Product second = product(2L, 2);

        when(reservationRepository.findById(100L)).thenReturn(Optional.empty());
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(first));
        when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.save(any(StockReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderCreatedPayload payload = new OrderCreatedPayload(100L, List.of(
                new OrderItemPayload(2L, 1),
                new OrderItemPayload(1L, 2)), new BigDecimal("42.00"));

        ReservationResult result = service.reserve(payload);

        assertThat(result.status()).isEqualTo("RESERVED");
        assertThat(first.getStock()).isEqualTo(3);
        assertThat(second.getStock()).isEqualTo(1);

        InOrder lockOrder = inOrder(productRepository);
        lockOrder.verify(productRepository).findByIdForUpdate(1L);
        lockOrder.verify(productRepository).findByIdForUpdate(2L);
        verify(reservationRepository).save(any(StockReservation.class));
    }

    @Test
    void rejectsWhenAnyItemHasInsufficientStock() {
        setUpService();
        Product product = product(1L, 1);

        when(reservationRepository.findById(101L)).thenReturn(Optional.empty());
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        OrderCreatedPayload payload = new OrderCreatedPayload(101L, List.of(
                new OrderItemPayload(1L, 2)), new BigDecimal("20.00"));

        assertThatThrownBy(() -> service.reserve(payload))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");

        assertThat(product.getStock()).isEqualTo(1);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void duplicateOrderEventReturnsThePreviousReservation() {
        setUpService();
        StockReservation existing = new StockReservation(102L, ReservationStatus.RESERVED, null);
        when(reservationRepository.findById(102L)).thenReturn(Optional.of(existing));

        ReservationResult result = service.reserve(new OrderCreatedPayload(
                102L, List.of(new OrderItemPayload(1L, 1)), BigDecimal.ONE));

        assertThat(result.status()).isEqualTo("RESERVED");
        verifyNoInteractions(productRepository);
    }

    @Test
    void storesRejectionInItsOwnTransaction() {
        setUpService();
        when(reservationRepository.findById(103L)).thenReturn(Optional.empty());
        when(reservationRepository.save(any(StockReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResult result = service.reject(103L, "No stock");

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.reason()).isEqualTo("No stock");
        verify(reservationRepository).save(any(StockReservation.class));
    }

    @Test
    void rejectsEmptyOrders() {
        setUpService();
        assertThatThrownBy(() -> service.reserve(new OrderCreatedPayload(
                104L, List.of(), BigDecimal.ZERO)))
                .isInstanceOf(InsufficientStockException.class);
        verifyNoInteractions(productRepository);
    }

    private Product product(Long id, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName("P" + id);
        product.setPrice(BigDecimal.TEN);
        product.setStock(stock);
        return product;
    }
}
