package hu.unimiskolc.shopstream.product;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * Step 4, task 5: stock reservation changes the product, so the cached entry
 * must go. @CacheEvict lives on a separate bean because a method called from
 * inside the same class would bypass the cache proxy.
 */
@Component
public class ProductCacheEvictor {

    @CacheEvict(cacheNames = "products", key = "#productId")
    public void evict(Long productId) {
        // The annotation does the work; the method body stays empty.
    }
}
