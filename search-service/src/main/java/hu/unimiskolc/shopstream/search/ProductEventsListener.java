package hu.unimiskolc.shopstream.search;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Step 3, tasks 3: consumes product-events and keeps the products index in sync
 * through the ElasticsearchRepository.
 */
@Component
public class ProductEventsListener {

    private static final Logger log = LoggerFactory.getLogger(ProductEventsListener.class);

    private final ProductSearchRepository repository;

    public ProductEventsListener(ProductSearchRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "${app.topics.product-events:product-events}", groupId = "search-service")
    public void onProductEvent(ConsumerRecord<String, EventMessage> record) {
        EventMessage event = record.value();
        if (event == null) {
            return;
        }
        log.info("Received {} key={} partition={} offset={}",
                event.eventType(), record.key(), record.partition(), record.offset());
        handleProductEvent(event);
    }

    void handleProductEvent(EventMessage event) {
        JsonNode payload = event.payload();
        if (payload == null) {
            return;
        }

        if ("ProductDeleted".equals(event.eventType())) {
            Long id = payload.path("id").asLong();
            if (repository.existsById(id)) {
                repository.deleteById(id);
            }
            log.info("Removed product {} from the search index", id);
            return;
        }

        if (!"ProductCreated".equals(event.eventType()) && !"ProductUpdated".equals(event.eventType())) {
            return;
        }

        try {
            ProductDocument document = new ProductDocument();
            document.setId(payload.path("id").asLong());
            document.setName(payload.path("name").asText(null));
            document.setDescription(payload.path("description").asText(null));
            document.setCategory(payload.path("category").asText(null));
            document.setPrice(payload.hasNonNull("price") ? payload.path("price").asDouble() : null);
            document.setStock(payload.hasNonNull("stock") ? payload.path("stock").asInt() : null);
            repository.save(document);
            log.info("Indexed product {} ({})", document.getId(), event.eventType());
        } catch (IllegalArgumentException ex) {
            log.error("Dropping invalid product payload: {}", payload, ex);
        }
    }
}
