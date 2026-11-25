package hu.unimiskolc.shopstream.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private ApplicationEventPublisher events;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(repository, events);
    }

    @Test
    void returnsTheProductFromTheDatabase() {
        Product product = product(1L, "Laptop", "1299.00", 5);
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        Product result = service.get(1L);

        assertThat(result).isSameAs(product);
        verify(repository).findById(1L);
    }

    @Test
    void throwsWhenProductDoesNotExist() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("9");
    }

    @Test
    void publishesAProductCreatedEventAfterSaving() {
        Product product = product(null, "Keyboard", "49.00", 10);
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        Product saved = service.create(product);

        assertThat(saved.getId()).isEqualTo(7L);
        verify(events).publishEvent(any(ProductChangedEvent.class));
    }

    @Test
    void deleteRemovesTheProductAndPublishesAnEvent() {
        Product product = product(3L, "Mouse", "19.00", 2);
        when(repository.findById(3L)).thenReturn(Optional.of(product));

        service.delete(3L);

        verify(repository).delete(product);
        verify(events).publishEvent(any(ProductChangedEvent.class));
    }

    @Test
    void updateRejectsMissingProduct() {
        when(repository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(4L, product(null, "x", "1.00", 1)))
                .isInstanceOf(ProductNotFoundException.class);
        verifyNoInteractions(events);
    }

    private Product product(Long id, String name, String price, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setDescription("description");
        product.setCategory("test");
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        return product;
    }
}
