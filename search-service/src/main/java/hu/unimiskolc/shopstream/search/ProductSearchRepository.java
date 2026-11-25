package hu.unimiskolc.shopstream.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Step 3, task 5: derived repository methods for the simple, single-criterion
 * cases. Spring Data translates the method name into a term query on the
 * keyword field, no manual query needed.
 */
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, Long> {

    Page<ProductDocument> findByCategory(String category, Pageable pageable);
}
