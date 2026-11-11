package hu.unimiskolc.shopstream.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

/**
 * Product use cases. Redis is used as an explicit cache-aside layer instead of
 * annotating controller methods, which keeps HTTP concerns out of the cache and
 * avoids caching 404 responses.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(2);
    private static final String CACHE_PREFIX = "product:";

    private final ProductRepository repository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public ProductService(ProductRepository repository,
                          StringRedisTemplate redis,
                          ObjectMapper objectMapper,
                          ApplicationEventPublisher events) {
        this.repository = repository;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    public record CachedProduct(Product product, boolean cacheHit) {
    }

    public List<Product> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public CachedProduct get(Long id) {
        String key = CACHE_PREFIX + id;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return new CachedProduct(objectMapper.readValue(cached, Product.class), true);
            }
        } catch (Exception ex) {
            // Redis must never make the catalogue unavailable.
            log.warn("Cache read failed for product {}: {}", id, ex.getMessage());
        }

        Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(product), CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Cache write failed for product {}: {}", id, ex.getMessage());
        }
        return new CachedProduct(product, false);
    }

    @Transactional
    public Product create(Product product) {
        product.setId(null);
        Product saved = repository.save(product);
        events.publishEvent(new ProductChangedEvent(
                ProductChangeType.CREATED, saved.getId(), ProductPayload.from(saved)));
        return saved;
    }

    @Transactional
    public Product update(Long id, Product body) {
        Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        product.setName(body.getName());
        product.setDescription(body.getDescription());
        product.setCategory(body.getCategory());
        product.setPrice(body.getPrice());
        product.setStock(body.getStock());
        Product saved = repository.save(product);
        evictAfterCommit(id);
        events.publishEvent(new ProductChangedEvent(
                ProductChangeType.UPDATED, saved.getId(), ProductPayload.from(saved)));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        repository.delete(product);
        evictAfterCommit(id);
        events.publishEvent(new ProductChangedEvent(ProductChangeType.DELETED, id, null));
    }

    /**
     * Evicting before the commit would let a concurrent reader cache the old
     * row again, so the key is removed only after the transaction is durable.
     * Outside a transaction (unit tests, direct calls) the eviction is immediate.
     */
    private void evictAfterCommit(Long id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(id);
                }
            });
        } else {
            evict(id);
        }
    }

    void evict(Long id) {
        try {
            redis.delete(CACHE_PREFIX + id);
        } catch (Exception ex) {
            log.warn("Cache eviction failed for product {}: {}", id, ex.getMessage());
        }
    }
}
