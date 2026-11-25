package hu.unimiskolc.shopstream.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Product use cases.
 *
 * <p>Step 4, task 5: the product read is cached declaratively. {@link Cacheable}
 * stores the returned entity in Redis under the {@code products} cache, and
 * {@link CacheEvict} removes it whenever the product is updated, deleted or its
 * stock is reserved ({@link ProductCacheEvictor}).</p>
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository repository;
    private final ApplicationEventPublisher events;

    public ProductService(ProductRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public List<Product> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    /**
     * The method body runs only on a cache miss, so this log line is the evidence
     * for the Redis demo: the second GET /products/{id} does not print it.
     */
    @Cacheable(cacheNames = "products", key = "#id")
    @Transactional(readOnly = true)
    public Product get(Long id) {
        log.info("Cache miss for product {} - reading from MySQL", id);
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product create(Product product) {
        product.setId(null);
        Product saved = repository.save(product);
        events.publishEvent(new ProductChangedEvent(
                ProductChangeType.CREATED, saved.getId(), ProductPayload.from(saved)));
        return saved;
    }

    @CacheEvict(cacheNames = "products", key = "#id")
    @Transactional
    public Product update(Long id, Product body) {
        Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        product.setName(body.getName());
        product.setDescription(body.getDescription());
        product.setCategory(body.getCategory());
        product.setPrice(body.getPrice());
        product.setStock(body.getStock());
        Product saved = repository.save(product);
        events.publishEvent(new ProductChangedEvent(
                ProductChangeType.UPDATED, saved.getId(), ProductPayload.from(saved)));
        return saved;
    }

    @CacheEvict(cacheNames = "products", key = "#id")
    @Transactional
    public void delete(Long id) {
        Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        repository.delete(product);
        events.publishEvent(new ProductChangedEvent(ProductChangeType.DELETED, id, null));
    }
}
