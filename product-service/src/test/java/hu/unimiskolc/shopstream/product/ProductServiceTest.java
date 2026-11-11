package hu.unimiskolc.shopstream.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ApplicationEventPublisher events;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProductService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        service = new ProductService(repository, redis, objectMapper, events);
    }

    @Test
    void returnsProductFromCacheWithoutTouchingTheDatabase() throws Exception {
        Product product = product(1L, "Laptop", "1299.00", 5);
        when(valueOperations.get("product:1")).thenReturn(objectMapper.writeValueAsString(product));

        ProductService.CachedProduct result = service.get(1L);

        assertThat(result.cacheHit()).isTrue();
        assertThat(result.product().getName()).isEqualTo("Laptop");
        verify(repository, never()).findById(any());
    }

    @Test
    void loadsFromDatabaseAndPopulatesCacheOnMiss() {
        Product product = product(1L, "Laptop", "1299.00", 5);
        when(valueOperations.get("product:1")).thenReturn(null);
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        ProductService.CachedProduct result = service.get(1L);

        assertThat(result.cacheHit()).isFalse();
        assertThat(result.product()).isSameAs(product);
        verify(valueOperations).set(eq("product:1"), anyString(), any(Duration.class));
    }

    @Test
    void throwsWhenProductDoesNotExist() {
        when(valueOperations.get("product:9")).thenReturn(null);
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
    void deleteEvictsTheCacheAndPublishesAnEvent() {
        Product product = product(3L, "Mouse", "19.00", 2);
        when(repository.findById(3L)).thenReturn(Optional.of(product));

        service.delete(3L);

        verify(repository).delete(product);
        verify(redis).delete("product:3");
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
