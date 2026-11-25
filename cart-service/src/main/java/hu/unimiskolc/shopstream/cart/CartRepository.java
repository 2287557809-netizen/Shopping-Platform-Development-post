package hu.unimiskolc.shopstream.cart;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Step 4, task 2: a plain CrudRepository keyed by the cart's @Id (userId). The
 * concrete Redis key is {@code carts:{userId}}, managed by @RedisHash.
 */
@Repository
public interface CartRepository extends CrudRepository<Cart, String> {
}
