package hu.unimiskolc.shopstream.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Creates the index with the explicit mapping from ProductDocument. Without this
 * Elasticsearch would guess field types from the first document.
 */
@Component
public class SearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);

    private final ElasticsearchOperations operations;

    public SearchIndexInitializer(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations index = operations.indexOps(ProductDocument.class);
        if (!index.exists()) {
            index.createWithMapping();
            log.info("Created Elasticsearch index 'products' with explicit mapping");
        } else {
            log.info("Elasticsearch index 'products' already exists");
        }
    }
}
